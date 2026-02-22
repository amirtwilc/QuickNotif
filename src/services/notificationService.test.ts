import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  mockLocalNotifications,
  mockPreferences,
  mockCapacitorCore,
  mockApp,
  mockFilesystem,
} from '@/test/mocks/capacitor';

// Mock Capacitor modules before importing the service
vi.mock('@capacitor/local-notifications', () => ({
  LocalNotifications: mockLocalNotifications,
}));

vi.mock('@capacitor/preferences', () => ({
  Preferences: mockPreferences,
}));

vi.mock('@capacitor/core', () => ({
  Capacitor: mockCapacitorCore,
}));

vi.mock('@capacitor/app', () => ({
  App: mockApp,
}));

vi.mock('@capacitor/filesystem', () => ({
  Filesystem: mockFilesystem,
  Directory: { Documents: 'DOCUMENTS' },
  Encoding: { UTF8: 'utf8' },
}));

// Mock logger to prevent filesystem calls in tests
vi.mock('./notificationLogger', () => ({
  default: {
    logSchedule: vi.fn().mockResolvedValue(undefined),
    logDelete: vi.fn().mockResolvedValue(undefined),
    logReactivate: vi.fn().mockResolvedValue(undefined),
    logError: vi.fn().mockResolvedValue(undefined),
    setService: vi.fn(),
  },
}));

import { NotificationService } from './notificationService';
import notificationLogger from './notificationLogger';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    // Reset singleton so each test gets a fresh instance
    (NotificationService as unknown as { instance: undefined }).instance = undefined;
    service = NotificationService.getInstance();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-02-19T10:00:00.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // ── initialize() ──────────────────────────────────────────────────────────

  describe('initialize()', () => {
    it('starts with empty notifications when localStorage is empty', async () => {
      await service.initialize();
      expect(service.getNotifications()).toEqual([]);
    });

    it('starts with empty savedNames when localStorage is empty', async () => {
      await service.initialize();
      expect(service.getSavedNames()).toEqual([]);
    });

    it('parses pre-existing notifications from localStorage', async () => {
      const stored = JSON.stringify([
        {
          id: 'notification_111_aaa',
          name: 'Test',
          time: '14:30',
          type: 'absolute',
          enabled: true,
          scheduledAt: '2026-02-19T14:30:00.000Z',
          updatedAt: '2026-02-19T10:00:00.000Z',
        },
      ]);
      localStorage.setItem('notifications', stored);

      await service.initialize();
      const notifications = service.getNotifications();
      expect(notifications).toHaveLength(1);
      expect(notifications[0].id).toBe('notification_111_aaa');
      expect(notifications[0].scheduledAt).toBeInstanceOf(Date);
      expect(notifications[0].updatedAt).toBeInstanceOf(Date);
    });

    it('falls back to createdAt when updatedAt is missing', async () => {
      const stored = JSON.stringify([
        {
          id: 'notification_222_bbb',
          name: 'Fallback',
          time: '09:00',
          type: 'absolute',
          enabled: true,
          scheduledAt: '2026-02-19T09:00:00.000Z',
          createdAt: '2026-02-19T08:00:00.000Z',
          // no updatedAt
        },
      ]);
      localStorage.setItem('notifications', stored);

      await service.initialize();
      const notifications = service.getNotifications();
      expect(notifications[0].updatedAt).toBeInstanceOf(Date);
      expect(notifications[0].updatedAt.toISOString()).toBe('2026-02-19T08:00:00.000Z');
    });

    it('parses pre-existing savedNames from localStorage', async () => {
      localStorage.setItem('savedNames', JSON.stringify(['Alice', 'Bob']));
      await service.initialize();
      expect(service.getSavedNames()).toEqual(['Alice', 'Bob']);
    });
  });

  // ── scheduleNotification() ────────────────────────────────────────────────

  describe('scheduleNotification()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('returns a string ID matching the expected pattern', async () => {
      const id = await service.scheduleNotification('Meeting', '14:30', 'absolute');
      expect(id).toMatch(/^notification_\d+_[a-z0-9]+$/);
    });

    it('adds the notification to getNotifications()', async () => {
      await service.scheduleNotification('Meeting', '14:30', 'absolute');
      expect(service.getNotifications()).toHaveLength(1);
      expect(service.getNotifications()[0].name).toBe('Meeting');
    });

    it('sets interval (ms) for relative notifications', async () => {
      await service.scheduleNotification('Reminder', '30 minutes', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.interval).toBe(30 * 60 * 1000); // 1800000 ms
    });

    it('sets interval for combined hour+minute relative notifications', async () => {
      await service.scheduleNotification('Reminder', '1 hour 30 minutes', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.interval).toBe(90 * 60 * 1000); // 5400000 ms
    });

    it('leaves interval undefined for absolute notifications', async () => {
      await service.scheduleNotification('Meeting', '14:30', 'absolute');
      const notification = service.getNotifications()[0];
      expect(notification.interval).toBeUndefined();
    });

    it('saves notification to localStorage', async () => {
      await service.scheduleNotification('Saved', '15:00', 'absolute');
      const stored = localStorage.getItem('notifications');
      expect(stored).not.toBeNull();
      const parsed = JSON.parse(stored!);
      expect(parsed).toHaveLength(1);
      expect(parsed[0].name).toBe('Saved');
    });

    it('adds name to savedNames', async () => {
      await service.scheduleNotification('UniqueNameAlpha', '14:30', 'absolute');
      expect(service.getSavedNames()).toContain('UniqueNameAlpha');
    });

    it('deduplicates savedNames and moves existing to front', async () => {
      await service.scheduleNotification('Alpha', '14:30', 'absolute');
      await service.scheduleNotification('Beta', '15:00', 'absolute');
      await service.scheduleNotification('Alpha', '16:00', 'absolute');
      const names = service.getSavedNames();
      expect(names[0]).toBe('Alpha');
      expect(names.filter(n => n === 'Alpha')).toHaveLength(1);
    });

    it('limits savedNames to 10 entries', async () => {
      for (let i = 0; i < 12; i++) {
        await service.scheduleNotification(`Name${i}`, '14:30', 'absolute');
      }
      expect(service.getSavedNames()).toHaveLength(10);
    });

    it('sets updatedAt to the current time', async () => {
      await service.scheduleNotification('Test', '14:30', 'absolute');
      const notification = service.getNotifications()[0];
      expect(notification.updatedAt).toBeInstanceOf(Date);
      expect(notification.updatedAt.getTime()).toBe(new Date('2026-02-19T10:00:00.000Z').getTime());
    });

    it('sets interval for hours-only relative notifications', async () => {
      await service.scheduleNotification('Reminder', '1 hour', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.interval).toBe(60 * 60 * 1000);
    });
  });

  // ── calculateScheduleTime (tested via scheduleNotification) ──────────────

  describe('calculateScheduleTime via scheduleNotification', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('absolute "14:30" at 10:00 UTC → schedules today (local time)', async () => {
      // System time is 2026-02-19T10:00:00.000Z
      // "14:30" is after 10:00 local, so it should be today
      await service.scheduleNotification('Test', '14:30', 'absolute');
      const notification = service.getNotifications()[0];
      const scheduled = notification.scheduledAt;
      expect(scheduled.getHours()).toBe(14);
      expect(scheduled.getMinutes()).toBe(30);
    });

    it('absolute "08:00" at 10:00 UTC → schedules tomorrow', async () => {
      // "08:00" is before 10:00 local, so it should be tomorrow
      await service.scheduleNotification('Test', '08:00', 'absolute');
      const notification = service.getNotifications()[0];
      const scheduled = notification.scheduledAt;
      const now = new Date('2026-02-19T10:00:00.000Z');
      expect(scheduled.getDate()).toBe(now.getDate() + 1);
      expect(scheduled.getHours()).toBe(8);
      expect(scheduled.getMinutes()).toBe(0);
    });

    it('relative "30 minutes" → scheduledAt = now + 1800000 ms', async () => {
      const now = Date.now(); // 2026-02-19T10:00:00.000Z
      await service.scheduleNotification('Test', '30 minutes', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.scheduledAt.getTime()).toBe(now + 30 * 60 * 1000);
    });

    it('relative "1 hour 30 minutes" → scheduledAt = now + 5400000 ms', async () => {
      const now = Date.now();
      await service.scheduleNotification('Test', '1 hour 30 minutes', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.scheduledAt.getTime()).toBe(now + 90 * 60 * 1000);
    });

    it('relative "1 hour" → scheduledAt = now + 3600000 ms', async () => {
      const now = Date.now();
      await service.scheduleNotification('Test', '1 hour', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.scheduledAt.getTime()).toBe(now + 60 * 60 * 1000);
    });
  });

  // ── deleteNotification() ──────────────────────────────────────────────────

  describe('deleteNotification()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('removes the notification from the list', async () => {
      const id = await service.scheduleNotification('ToDelete', '14:30', 'absolute');
      await service.deleteNotification(id);
      expect(service.getNotifications()).toHaveLength(0);
    });

    it('removes the notification from localStorage', async () => {
      const id = await service.scheduleNotification('ToDelete', '14:30', 'absolute');
      await service.deleteNotification(id);
      const stored = localStorage.getItem('notifications');
      expect(JSON.parse(stored!)).toHaveLength(0);
    });

    it('does not crash when deleting a nonexistent ID', async () => {
      await expect(service.deleteNotification('nonexistent_id')).resolves.not.toThrow();
    });

    it('does not change state when deleting a nonexistent ID', async () => {
      await service.scheduleNotification('Keeper', '14:30', 'absolute');
      await service.deleteNotification('nonexistent_id');
      expect(service.getNotifications()).toHaveLength(1);
    });
  });

  // ── toggleNotification() ──────────────────────────────────────────────────

  describe('toggleNotification()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('disables an enabled notification', async () => {
      const id = await service.scheduleNotification('Toggle', '14:30', 'absolute');
      await service.toggleNotification(id);
      expect(service.getNotifications()[0].enabled).toBe(false);
    });

    it('enables a disabled notification', async () => {
      const id = await service.scheduleNotification('Toggle', '14:30', 'absolute');
      await service.toggleNotification(id); // disable
      await service.toggleNotification(id); // re-enable
      expect(service.getNotifications()[0].enabled).toBe(true);
    });

    it('saves updated state to localStorage', async () => {
      const id = await service.scheduleNotification('Toggle', '14:30', 'absolute');
      await service.toggleNotification(id);
      const stored = JSON.parse(localStorage.getItem('notifications')!);
      expect(stored[0].enabled).toBe(false);
    });

    it('does nothing when the ID does not exist', async () => {
      await service.scheduleNotification('Keeper', '14:30', 'absolute');
      await expect(service.toggleNotification('nonexistent_id')).resolves.not.toThrow();
      expect(service.getNotifications()).toHaveLength(1);
      expect(service.getNotifications()[0].enabled).toBe(true);
    });
  });

  // ── updateNotificationTime() ──────────────────────────────────────────────

  describe('updateNotificationTime()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('changes type from absolute to relative and sets interval', async () => {
      const id = await service.scheduleNotification('Edit', '14:30', 'absolute');
      await service.updateNotificationTime(id, '45 minutes', 'relative');
      const notification = service.getNotifications()[0];
      expect(notification.type).toBe('relative');
      expect(notification.interval).toBe(45 * 60 * 1000);
    });

    it('changes type from relative to absolute and clears interval', async () => {
      const id = await service.scheduleNotification('Edit', '30 minutes', 'relative');
      await service.updateNotificationTime(id, '16:00', 'absolute');
      const notification = service.getNotifications()[0];
      expect(notification.type).toBe('absolute');
      expect(notification.interval).toBeUndefined();
    });

    it('re-enables a disabled notification', async () => {
      const id = await service.scheduleNotification('Edit', '14:30', 'absolute');
      await service.toggleNotification(id); // disable
      await service.updateNotificationTime(id, '15:00', 'absolute');
      expect(service.getNotifications()[0].enabled).toBe(true);
    });

    it('does nothing when the ID does not exist', async () => {
      await service.scheduleNotification('Keeper', '14:30', 'absolute');
      await expect(service.updateNotificationTime('nonexistent_id', '15:00', 'absolute')).resolves.not.toThrow();
      expect(service.getNotifications()[0].time).toBe('14:30');
    });
  });

  // ── getNotifications() ────────────────────────────────────────────────────

  describe('getNotifications()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('returns notifications sorted by scheduledAt ascending', async () => {
      // Schedule a later notification first
      await service.scheduleNotification('Later', '16:00', 'absolute');
      await service.scheduleNotification('Earlier', '14:00', 'absolute');
      const notifications = service.getNotifications();
      expect(notifications[0].name).toBe('Earlier');
      expect(notifications[1].name).toBe('Later');
    });

    it('returns a copy — mutating the result does not affect internal state', async () => {
      await service.scheduleNotification('Original', '14:30', 'absolute');
      const first = service.getNotifications();
      first.pop(); // mutate the returned array
      expect(service.getNotifications()).toHaveLength(1);
    });

    it('returns an empty array when no notifications exist', async () => {
      expect(service.getNotifications()).toEqual([]);
    });
  });

  // ── reactivateNotification() ──────────────────────────────────────────────

  describe('reactivateNotification()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('does nothing when the ID does not exist', async () => {
      await service.scheduleNotification('Keeper', '14:30', 'absolute');
      await expect(service.reactivateNotification('nonexistent_id')).resolves.not.toThrow();
      expect(service.getNotifications()).toHaveLength(1);
    });

    it('re-schedules an absolute notification using its existing time', async () => {
      const id = await service.scheduleNotification('Meeting', '14:30', 'absolute');
      vi.setSystemTime(new Date('2026-02-20T15:00:00.000Z')); // advance past scheduled time
      await service.reactivateNotification(id);
      const notification = service.getNotifications()[0];
      expect(notification.scheduledAt.getHours()).toBe(14);
      expect(notification.scheduledAt.getMinutes()).toBe(30);
      expect(notification.enabled).toBe(true);
    });

    it('re-schedules a relative notification from the current time', async () => {
      const id = await service.scheduleNotification('Reminder', '30 minutes', 'relative');
      vi.setSystemTime(new Date('2026-02-19T11:00:00.000Z'));
      await service.reactivateNotification(id);
      const notification = service.getNotifications()[0];
      expect(notification.scheduledAt.getTime()).toBe(
        new Date('2026-02-19T11:00:00.000Z').getTime() + 30 * 60 * 1000
      );
    });

    it('calls logReactivate with the correct arguments', async () => {
      const id = await service.scheduleNotification('LogTest', '14:30', 'absolute');
      await service.reactivateNotification(id);
      expect(notificationLogger.logReactivate).toHaveBeenCalledWith(
        id,
        'LogTest',
        expect.any(Number)
      );
    });
  });

  // ── refresh() ─────────────────────────────────────────────────────────────

  describe('refresh()', () => {
    beforeEach(async () => {
      await service.initialize();
    });

    it('reloads notifications written to localStorage after initialization', async () => {
      const stored = JSON.stringify([{
        id: 'notification_999_zzz',
        name: 'External',
        time: '16:00',
        type: 'absolute',
        enabled: true,
        scheduledAt: '2026-02-19T16:00:00.000Z',
        updatedAt: '2026-02-19T10:00:00.000Z',
      }]);
      localStorage.setItem('notifications', stored);
      await service.refresh();
      expect(service.getNotifications()).toHaveLength(1);
      expect(service.getNotifications()[0].name).toBe('External');
    });

    it('reflects a deletion made directly in localStorage', async () => {
      await service.scheduleNotification('ToRemove', '14:30', 'absolute');
      localStorage.setItem('notifications', JSON.stringify([]));
      await service.refresh();
      expect(service.getNotifications()).toHaveLength(0);
    });
  });

  // ── setPermissionCallbacks() / completePermissionSetup() ──────────────────

  describe('setPermissionCallbacks() and completePermissionSetup()', () => {
    it('fires onStepChange("complete") when completePermissionSetup is called', async () => {
      const onStepChange = vi.fn();
      service.setPermissionCallbacks({ onStepChange });
      await service.completePermissionSetup();
      expect(onStepChange).toHaveBeenCalledWith('complete');
    });

    it('does not throw when no callbacks are registered', async () => {
      await expect(service.completePermissionSetup()).resolves.not.toThrow();
    });
  });

  // ── native platform ────────────────────────────────────────────────────────

  describe('on native platform', () => {
    beforeEach(() => {
      mockCapacitorCore.isNativePlatform.mockReturnValue(true);
    });

    afterEach(() => {
      mockCapacitorCore.isNativePlatform.mockReturnValue(false);
    });

    describe('initialize()', () => {
      it('throws and fires onStepChange("notification") when permission is denied', async () => {
        mockLocalNotifications.checkPermissions.mockResolvedValueOnce({ display: 'denied' });
        const onStepChange = vi.fn();
        service.setPermissionCallbacks({ onStepChange });
        await expect(service.initialize()).rejects.toThrow('Notification permission not granted');
        expect(onStepChange).toHaveBeenCalledWith('notification');
      });

      it('creates notification channel when permission is granted', async () => {
        await service.initialize();
        expect(mockLocalNotifications.createChannel).toHaveBeenCalled();
      });

      it('loads notifications from Preferences', async () => {
        const stored = JSON.stringify([{
          id: 'notification_111_aaa',
          name: 'Restored',
          time: '14:30',
          type: 'absolute',
          enabled: true,
          scheduledAt: '2026-02-19T14:30:00.000Z',
          updatedAt: '2026-02-19T10:00:00.000Z',
        }]);
        mockPreferences.get.mockImplementation(async ({ key }: { key: string }) => ({
          value: key === 'notifications' ? stored : null,
        }));
        await service.initialize();
        expect(service.getNotifications()).toHaveLength(1);
        expect(service.getNotifications()[0].name).toBe('Restored');
      });

      it('loads savedNames from Preferences', async () => {
        mockPreferences.get.mockImplementation(async ({ key }: { key: string }) => ({
          value: key === 'savedNames' ? JSON.stringify(['Alice', 'Bob']) : null,
        }));
        await service.initialize();
        expect(service.getSavedNames()).toEqual(['Alice', 'Bob']);
      });
    });

    describe('requestNotificationPermission()', () => {
      it('returns true and creates channel when granted', async () => {
        const result = await service.requestNotificationPermission();
        expect(result).toBe(true);
        expect(mockLocalNotifications.createChannel).toHaveBeenCalled();
      });

      it('returns false and fires onPermissionDenied when denied', async () => {
        mockLocalNotifications.requestPermissions.mockResolvedValueOnce({ display: 'denied' });
        const onPermissionDenied = vi.fn();
        service.setPermissionCallbacks({ onPermissionDenied });
        const result = await service.requestNotificationPermission();
        expect(result).toBe(false);
        expect(onPermissionDenied).toHaveBeenCalled();
      });
    });

    describe('scheduleNotification()', () => {
      beforeEach(async () => {
        await service.initialize();
      });

      it('removes notification from list and throws when LocalNotifications.schedule fails', async () => {
        mockLocalNotifications.schedule.mockRejectedValueOnce(new Error('AlarmManager error'));
        await expect(service.scheduleNotification('Test', '14:30', 'absolute'))
          .rejects.toThrow('Failed to schedule notification');
        expect(service.getNotifications()).toHaveLength(0);
      });

      it('removes notification from list and throws when not found in pending after retries', async () => {
        mockLocalNotifications.getPending.mockResolvedValue({ notifications: [] });
        vi.useRealTimers();
        await expect(service.scheduleNotification('Test', '14:30', 'absolute'))
          .rejects.toThrow('Notification was not added to pending list');
        expect(service.getNotifications()).toHaveLength(0);
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-02-19T10:00:00.000Z'));
      }, 10000);

      it('saves to Preferences and calls refreshWidget on success', async () => {
        let scheduledId: number | undefined;
        mockLocalNotifications.schedule.mockImplementation(
          async ({ notifications }: { notifications: Array<{ id: number }> }) => {
            scheduledId = notifications[0].id;
            return {};
          }
        );
        mockLocalNotifications.getPending.mockImplementation(async () => ({
          notifications: scheduledId !== undefined ? [{ id: scheduledId }] : [],
        }));

        const promise = service.scheduleNotification('Test', '14:30', 'absolute');
        await vi.runAllTimersAsync();
        await promise;

        expect(mockPreferences.set).toHaveBeenCalledWith(
          expect.objectContaining({ key: 'notifications' })
        );
        expect(window.Android!.refreshWidget).toHaveBeenCalled();
      });
    });

    describe('toggleNotification()', () => {
      let id: string;

      beforeEach(async () => {
        // Schedule in web mode to avoid native scheduling complexity
        mockCapacitorCore.isNativePlatform.mockReturnValue(false);
        await service.initialize();
        id = await service.scheduleNotification('Meeting', '14:30', 'absolute');
        mockCapacitorCore.isNativePlatform.mockReturnValue(true);
      });

      it('disabling calls LocalNotifications.cancel and cancelAlarmManagerNotification', async () => {
        await service.toggleNotification(id);
        expect(mockLocalNotifications.cancel).toHaveBeenCalledWith({
          notifications: [{ id: expect.any(Number) }],
        });
        expect(window.Android!.cancelAlarmManagerNotification).toHaveBeenCalledWith(id);
      });

      it('enabling with a future scheduledAt calls LocalNotifications.schedule', async () => {
        await service.toggleNotification(id); // disable
        vi.clearAllMocks();
        await service.toggleNotification(id); // re-enable
        expect(mockLocalNotifications.schedule).toHaveBeenCalled();
      });

      it('enabling with a past scheduledAt recalculates and reschedules for next occurrence', async () => {
        vi.setSystemTime(new Date('2026-02-19T15:00:00.000Z')); // advance past 14:30
        await service.toggleNotification(id); // disable
        vi.clearAllMocks();
        await service.toggleNotification(id); // re-enable — scheduledAt is in the past
        expect(mockLocalNotifications.schedule).toHaveBeenCalled();
        const notification = service.getNotifications()[0];
        expect(notification.scheduledAt.getHours()).toBe(14);
        expect(notification.scheduledAt.getMinutes()).toBe(30);
        // Should have moved to tomorrow since 14:30 has passed at 15:00
        expect(notification.scheduledAt.getDate()).toBe(20);
      });
    });
  });
});
