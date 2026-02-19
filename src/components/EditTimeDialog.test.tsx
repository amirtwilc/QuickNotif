import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EditTimeDialog } from './EditTimeDialog';

function renderDialog(overrides: Partial<Parameters<typeof EditTimeDialog>[0]> = {}) {
  const props = {
    open: true,
    onOpenChange: vi.fn(),
    onSave: vi.fn(),
    notificationName: 'Test Notification',
    currentTime: '14:30',
    currentType: 'absolute' as const,
    ...overrides,
  };
  return { ...render(<EditTimeDialog {...props} />), props };
}

describe('EditTimeDialog', () => {
  it('renders the notification name in the dialog description', () => {
    renderDialog({ notificationName: 'My Important Task' });
    expect(screen.getByText(/"My Important Task"/)).toBeInTheDocument();
  });

  it('falls back to "Unnamed notification" when name is empty', () => {
    renderDialog({ notificationName: '' });
    expect(screen.getByText(/"Unnamed notification"/)).toBeInTheDocument();
  });

  it('renders the Cancel button', () => {
    renderDialog();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  it('clicking Cancel calls onOpenChange(false) without calling onSave', async () => {
    const user = userEvent.setup();
    const { props } = renderDialog();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(props.onOpenChange).toHaveBeenCalledWith(false);
    expect(props.onSave).not.toHaveBeenCalled();
  });

  it('TimeInput is rendered inside the dialog', () => {
    renderDialog();
    // TimeInput renders Duration/Exact Time tabs
    expect(screen.getByRole('tab', { name: /Duration/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Exact Time/i })).toBeInTheDocument();
  });

  it('clicking Schedule in TimeInput calls onSave with the submitted time and type', async () => {
    const user = userEvent.setup();
    const { props } = renderDialog();
    // Use the relative tab (active by default) and fill in minutes
    await user.type(screen.getByLabelText('Minutes'), '45');
    await user.click(screen.getByRole('button', { name: /Schedule in/i }));
    expect(props.onSave).toHaveBeenCalledWith('45 minutes', 'relative');
  });
});
