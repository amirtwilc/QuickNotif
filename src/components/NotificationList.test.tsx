import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationList } from './NotificationList';
import type { NotificationItem } from '@/services/notificationService';

const NOW = new Date();
const ONE_HOUR_FROM_NOW = new Date(NOW.getTime() + 60 * 60 * 1000);
const ONE_HOUR_AGO = new Date(NOW.getTime() - 60 * 60 * 1000);
const TOMORROW = new Date(NOW.getTime() + 25 * 60 * 60 * 1000);

function makeNotification(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id: 'notification_test_001',
    name: 'Test Notification',
    time: '14:30',
    type: 'absolute',
    enabled: true,
    scheduledAt: ONE_HOUR_FROM_NOW,
    updatedAt: ONE_HOUR_AGO, // updatedAt in the past keeps "Last updated" unambiguous
    ...overrides,
  };
}

function renderList(
  notifications: NotificationItem[],
  handlers: {
    onToggle?: (id: string) => void;
    onDelete?: (id: string) => void;
    onEdit?: (id: string, time: string, type: 'absolute' | 'relative') => void;
    onReactivate?: (id: string) => void;
  } = {}
) {
  return render(
    <NotificationList
      notifications={notifications}
      onToggle={handlers.onToggle ?? vi.fn()}
      onDelete={handlers.onDelete ?? vi.fn()}
      onEdit={handlers.onEdit ?? vi.fn()}
      onReactivate={handlers.onReactivate ?? vi.fn()}
    />
  );
}

/** Find the button with aria-label="Reactivate" */
function getReactivateButton() {
  return screen.getByRole('button', { name: 'Reactivate' });
}

/** Find the delete button by its destructive CSS class */
function getDeleteButton() {
  return screen
    .getAllByRole('button')
    .find((b) => b.classList.contains('text-destructive'))!;
}

/** Find the edit (pencil) button by its primary class without aria-label */
function getEditButton() {
  return screen
    .getAllByRole('button')
    .find((b) => b.classList.contains('text-primary') && !b.getAttribute('aria-label'))!;
}

describe('NotificationList', () => {
  describe('empty state', () => {
    it('renders "No notifications scheduled" when list is empty', () => {
      renderList([]);
      expect(screen.getByText('No notifications scheduled')).toBeInTheDocument();
    });
  });

  describe('non-expired notification', () => {
    it('shows the notification name', () => {
      renderList([makeNotification({ name: 'Important Meeting' })]);
      expect(screen.getByText('Important Meeting')).toBeInTheDocument();
    });

    it('shows "Today at HH:mm" somewhere in the scheduled finish time', () => {
      // scheduledAt = ONE_HOUR_FROM_NOW (today), updatedAt = ONE_HOUR_AGO (today)
      // Both will show "Today at …"; use getAllByText
      renderList([makeNotification()]);
      expect(screen.getAllByText(/Today at \d{2}:\d{2}/)).not.toHaveLength(0);
    });

    it('shows "Tomorrow at HH:mm" for a notification scheduled tomorrow', () => {
      renderList([makeNotification({ scheduledAt: TOMORROW })]);
      expect(screen.getByText(/Tomorrow at \d{2}:\d{2}/)).toBeInTheDocument();
    });

    it('does NOT show "Expired" badge for future notification', () => {
      renderList([makeNotification()]);
      expect(screen.queryByText('Expired')).not.toBeInTheDocument();
    });

    it('switch is enabled for a non-expired notification', () => {
      renderList([makeNotification({ enabled: true })]);
      expect(screen.getByRole('switch')).not.toBeDisabled();
    });
  });

  describe('expired notification', () => {
    const expiredNotif = makeNotification({
      scheduledAt: ONE_HOUR_AGO,
      updatedAt: new Date(ONE_HOUR_AGO.getTime() - 60 * 60 * 1000),
      name: 'Old Task',
    });

    it('shows "Expired" badge', () => {
      renderList([expiredNotif]);
      expect(screen.getByText('Expired')).toBeInTheDocument();
    });

    it('switch is disabled for an expired notification', () => {
      renderList([expiredNotif]);
      expect(screen.getByRole('switch')).toBeDisabled();
    });
  });

  describe('delete action', () => {
    it('opens ConfirmDialog for active (non-expired) notification instead of deleting immediately', async () => {
      const user = userEvent.setup();
      const onDelete = vi.fn();
      renderList([makeNotification({ name: 'Active Task' })], { onDelete });
      await user.click(getDeleteButton());
      expect(screen.getByText('Delete notification?')).toBeInTheDocument();
      expect(onDelete).not.toHaveBeenCalled();
    });

    it('calls onDelete directly for expired notifications (no confirm dialog)', async () => {
      const user = userEvent.setup();
      const onDelete = vi.fn();
      const expiredNotif = makeNotification({
        scheduledAt: ONE_HOUR_AGO,
        updatedAt: new Date(ONE_HOUR_AGO.getTime() - 60 * 60 * 1000),
        id: 'expired-id',
      });
      renderList([expiredNotif], { onDelete });
      await user.click(getDeleteButton());
      expect(onDelete).toHaveBeenCalledWith('expired-id');
      expect(screen.queryByText('Delete notification?')).not.toBeInTheDocument();
    });
  });

  describe('toggle action', () => {
    it('calls onToggle with the notification id when switch is clicked', async () => {
      const user = userEvent.setup();
      const onToggle = vi.fn();
      const notif = makeNotification({ id: 'test-toggle-id' });
      renderList([notif], { onToggle });
      await user.click(screen.getByRole('switch'));
      expect(onToggle).toHaveBeenCalledWith('test-toggle-id');
    });
  });

  describe('edit action', () => {
    it('opens EditTimeDialog when edit button is clicked', async () => {
      const user = userEvent.setup();
      renderList([makeNotification({ name: 'Editable Task' })]);
      await user.click(getEditButton());
      expect(screen.getByText('Edit Notification Time')).toBeInTheDocument();
    });
  });

  describe('reactivate action', () => {
    it('opens ConfirmDialog for active notifications when reactivate is clicked', async () => {
      const user = userEvent.setup();
      const onReactivate = vi.fn();
      const activeNotif = makeNotification({ name: 'Active Task', enabled: true });
      renderList([activeNotif], { onReactivate });
      await user.click(getReactivateButton());
      expect(screen.getByText('Reschedule notification?')).toBeInTheDocument();
      expect(onReactivate).not.toHaveBeenCalled();
    });

    it('calls onReactivate directly for expired notifications', async () => {
      const user = userEvent.setup();
      const onReactivate = vi.fn();
      const expiredNotif = makeNotification({
        scheduledAt: ONE_HOUR_AGO,
        updatedAt: new Date(ONE_HOUR_AGO.getTime() - 60 * 60 * 1000),
        id: 'expired-id',
      });
      renderList([expiredNotif], { onReactivate });
      await user.click(getReactivateButton());
      expect(onReactivate).toHaveBeenCalledWith('expired-id');
    });
  });
});
