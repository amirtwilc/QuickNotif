import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';
import notificationLogger from './notificationLogger';
import { toNumericId } from '@/utils/notificationUtils';
import { NOTIFICATION_CONFIG } from '@/constants/notifications';

// Android bridge type definitions
declare global {
  interface Window {
    Android?: {
      isBatteryOptimized(): boolean;
      openBatterySettings(): void;
      openAutoStartSettings(): boolean;
      openAppSettings(): void;
      isAlarmScheduled(notificationId: number): boolean;
      checkAllAlarms(notificationIdsJson: string): string;
      cancelAlarmManagerNotification(notificationId: string): void;
      refreshWidget(): void;
    };
  }
}

export interface NotificationItem {
  id: string;
  name: string;
  time: string;
  type: 'absolute' | 'relative';
  enabled: boolean;
  scheduledAt: Date;
  updatedAt: Date;
  interval?: number;
}

interface StoredNotification {
  id: string;
  name: string;
  time: string;
  type: 'absolute' | 'relative';
  enabled: boolean;
  scheduledAt: string;
  updatedAt?: string;
  createdAt?: string;
  interval?: number;
}

export type PermissionStep = 'notification' | 'battery' | 'autostart' | 'complete';

export class NotificationService {
  private static instance: NotificationService;
  private notifications: NotificationItem[] = [];
  private savedNames: string[] = [];
  private permissionCallbacks: {
    onStepChange?: (step: PermissionStep) => void;
    onComplete?: () => void;
    onPermissionDenied?: () => void;
  } = {};

  /**
   * Parse relative time string like "15 minutes" or "2 hours 30 minutes"
   * @returns milliseconds
   */
  private parseRelativeTimeToMs(time: string): number {
    const parts = time.toLowerCase().split(' ');
    let totalMinutes = 0;

    for (let i = 0; i < parts.length; i += 2) {
      const value = parseInt(parts[i]);
      const unit = parts[i + 1] || '';
      if (Number.isFinite(value)) {
        if (unit.includes('hour')) totalMinutes += value * 60;
        else if (unit.includes('minute')) totalMinutes += value;
      }
    }

    return totalMinutes * 60 * 1000;
  }

  /**
   * Schedule notification with Android's LocalNotifications plugin
   */
  private async scheduleLocalNotification(
    id: string,
    name: string,
    scheduledAt: Date
  ): Promise<void> {
    const atDate = scheduledAt.getTime() <= Date.now() + NOTIFICATION_CONFIG.MIN_SCHEDULE_DELAY_MS
      ? new Date(Date.now() + NOTIFICATION_CONFIG.FALLBACK_DELAY_MS)
      : scheduledAt;

    await LocalNotifications.schedule({
      notifications: [{
        title: NOTIFICATION_CONFIG.TITLE,
        body: name,
        id: toNumericId(id),
        schedule: {
          at: atDate,
          allowWhileIdle: true,
          repeats: false
        },
        channelId: NOTIFICATION_CONFIG.CHANNEL_ID,
        attachments: undefined,
        actionTypeId: '',
        extra: {
          wakeUp: true,
          exactTiming: true
        },
        ongoing: false,
        autoCancel: true,
        largeBody: name,
        summaryText: '',
        smallIcon: NOTIFICATION_CONFIG.SMALL_ICON,
        largeIcon: NOTIFICATION_CONFIG.LARGE_ICON,
        iconColor: NOTIFICATION_CONFIG.ICON_COLOR,
        threadIdentifier: NOTIFICATION_CONFIG.THREAD_ID
      }]
    });
  }

  static getInstance(): NotificationService {
    if (!NotificationService.instance) {
      NotificationService.instance = new NotificationService();
    }
    return NotificationService.instance;
  }

  setPermissionCallbacks(callbacks: {
    onStepChange?: (step: PermissionStep) => void;
    onComplete?: () => void;
    onPermissionDenied?: () => void;
  }) {
    this.permissionCallbacks = callbacks;
  }

  async initialize() {
    if (Capacitor.isNativePlatform()) {
      // Always check current permission status
      const permission = await LocalNotifications.checkPermissions();

      if (permission.display !== 'granted') {
        // Show notification permission dialog
        this.permissionCallbacks.onStepChange?.('notification');
        throw new Error('Notification permission not granted');
      }

      // Check if battery optimization is disabled
      const batteryOptimized = await this.checkBatteryOptimization();
      if (batteryOptimized) {
        // Battery optimization is still ON, need to prompt user
        this.permissionCallbacks.onStepChange?.('battery');
        // Don't throw error, just show the dialog and continue loading data
        await this.setupNotificationChannel();
      } else {
        // Everything is good, setup channel
        await this.setupNotificationChannel();
      }
    }

    await this.loadFromStorage();
  }

  async checkBatteryOptimization(): Promise<boolean> {
    if (!Capacitor.isNativePlatform()) return false;

    try {
      if (window.Android?.isBatteryOptimized) {
        return window.Android.isBatteryOptimized();
      }
    } catch (e) {
      console.error('Failed to check battery optimization:', e);
    }

    // If we can't check, assume it's optimized to be safe
    return true;
  }

  async requestNotificationPermission(): Promise<boolean> {
    if (!Capacitor.isNativePlatform()) return true;

    // Request permission
    const permission = await LocalNotifications.requestPermissions();

    if (permission.display === 'granted') {
      await this.setupNotificationChannel();
      return true;
    } else {
      // Permission was denied
      this.permissionCallbacks.onPermissionDenied?.();
      return false;
    }
  }

  async requestBatteryOptimization(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;

    // Check if battery optimization is already disabled
    const isOptimized = await this.checkBatteryOptimization();

    if (isOptimized) {
      // Show the battery settings guidance dialog
      this.permissionCallbacks.onStepChange?.('battery');
    } else {
      // Battery optimization already disabled, skip to next step
      await this.requestAutoStartPermission();
    }
  }

  async openBatterySettings(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;

    try {
      if (window.Android?.openBatterySettings) {
        window.Android.openBatterySettings();
      }
    } catch (e) {
      console.error('Failed to open battery settings:', e);
    }
  }

  async requestAutoStartPermission(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;

    this.permissionCallbacks.onStepChange?.('autostart');
  }

  async openAutoStartSettings(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;

    try {
      if (window.Android?.openAutoStartSettings) {
        const opened = window.Android.openAutoStartSettings();
        if (!opened) {
          // Fallback to app settings if manufacturer-specific settings not available
          this.openAppSettings();
        }
      } else {
        // Fallback to app settings
        this.openAppSettings();
      }
    } catch (e) {
      console.error('Failed to open auto-start settings:', e);
      // Fallback to app settings
      this.openAppSettings();
    }
  }

  async openAppSettings(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;

    try {
      if (window.Android?.openAppSettings) {
        window.Android.openAppSettings();
      }
    } catch (e) {
      console.error('Failed to open app settings:', e);
    }
  }

  async verifyBatteryOptimization(): Promise<boolean> {
    // Re-check battery optimization status
    const isOptimized = await this.checkBatteryOptimization();
    return !isOptimized; // Return true if NOT optimized (i.e., user set it correctly)
  }

  async completePermissionSetup(): Promise<void> {
    this.permissionCallbacks.onStepChange?.('complete');
  }

  private async setupNotificationChannel(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;

    try {
      // Always recreate channel (idempotent operation)
      await LocalNotifications.createChannel({
        id: NOTIFICATION_CONFIG.CHANNEL_ID,
        name: NOTIFICATION_CONFIG.CHANNEL_NAME,
        description: NOTIFICATION_CONFIG.CHANNEL_DESCRIPTION,
        importance: NOTIFICATION_CONFIG.CHANNEL_IMPORTANCE,
        visibility: NOTIFICATION_CONFIG.CHANNEL_VISIBILITY,
        vibration: true,
        lights: true,
        lightColor: NOTIFICATION_CONFIG.ICON_COLOR,
        sound: 'default' // Add sound
      });
    } catch (e) {
      console.error('Channel creation failed', e);
      throw e; // Fail loudly
    }
  }

  async scheduleNotification(name: string, time: string, type: 'absolute' | 'relative'): Promise<string> {
    // Ensure channel exists before scheduling
    await this.setupNotificationChannel();

    const id = `notification_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const scheduledAt = this.calculateScheduleTime(time, type);

    // Store interval (ms) for relative notifications so the widget can reactivate correctly
    const intervalMs = type === 'relative' ? this.parseRelativeTimeToMs(time) : undefined;

    const notification: NotificationItem = {
      id,
      name,
      time,
      type,
      enabled: true,
      scheduledAt,
      updatedAt: new Date(),
      interval: intervalMs
    };

    this.notifications.push(notification);
    this.addToSavedNames(name);

    await notificationLogger.logSchedule(id, name, scheduledAt.getTime(), type);

    if (Capacitor.isNativePlatform()) {
      try {
        await this.scheduleLocalNotification(id, name, scheduledAt);

        // Verify and log result
        await new Promise(resolve => setTimeout(resolve, 100));
        const verified = await this.verifyNotificationScheduled(id);

        // Verify it was actually scheduled
        const pending = await LocalNotifications.getPending();
        const wasScheduled = pending.notifications.some(n => n.id === toNumericId(id));

        if (!wasScheduled) {
          throw new Error('Notification was not added to pending list');
        }

      } catch (e) {
        await notificationLogger.logError('Schedule failed', e, id);
        console.error('Scheduling failed', e);

        // Delete from list since it didn't actually schedule
        this.notifications = this.notifications.filter(n => n.id !== id);

        // Re-throw so UI can show error
        throw new Error('Failed to schedule notification: ' + (e as Error).message);
      }
    }

    this.saveToStorage();
    return id;
  }

  async toggleNotification(id: string): Promise<void> {
    const notification = this.notifications.find(n => n.id === id);
    if (!notification) return;

    notification.enabled = !notification.enabled;
    notification.updatedAt = new Date();

    if (Capacitor.isNativePlatform()) {
      const numericId = toNumericId(id);

      if (notification.enabled) {
        const atDate = notification.scheduledAt.getTime() <= Date.now()
          ? this.calculateScheduleTime(notification.time, notification.type)
          : notification.scheduledAt;
        notification.scheduledAt = atDate;
        try {
          await this.scheduleLocalNotification(id, notification.name, atDate);
        } catch (e) {
          console.error('Scheduling (toggle) failed', e);
        }
      } else {
        // Cancel Capacitor notification
        await LocalNotifications.cancel({ notifications: [{ id: numericId }] });
        // Also cancel AlarmManager alarm (in case it was scheduled by widget)
        if (window.Android?.cancelAlarmManagerNotification) {
          window.Android.cancelAlarmManagerNotification(id);
        }
      }
    }

    this.saveToStorage();
  }

  async deleteNotification(id: string): Promise<void> {
    // Get notification before removing it (for logging)
    const notification = this.notifications.find(n => n.id === id);

    if (Capacitor.isNativePlatform()) {
      const numericId = toNumericId(id);
      // Cancel Capacitor notification
      await LocalNotifications.cancel({ notifications: [{ id: numericId }] });
      // Also cancel AlarmManager alarm (in case it was scheduled by widget)
      if (window.Android?.cancelAlarmManagerNotification) {
        window.Android.cancelAlarmManagerNotification(id);
      }
    }

    this.notifications = this.notifications.filter(n => n.id !== id);

    if (notification) {
      await notificationLogger.logDelete(id, notification.name);
    }

    this.saveToStorage();
  }

  async updateNotificationTime(id: string, time: string, type: 'absolute' | 'relative'): Promise<void> {
    const notification = this.notifications.find(n => n.id === id);
    if (!notification) return;

    // Cancel existing notification if it exists
    if (Capacitor.isNativePlatform()) {
      const numericId = toNumericId(id);
      // Cancel Capacitor notification
      await LocalNotifications.cancel({ notifications: [{ id: numericId }] });
      // Also cancel AlarmManager alarm (in case it was scheduled by widget)
      if (window.Android?.cancelAlarmManagerNotification) {
        window.Android.cancelAlarmManagerNotification(id);
      }
    }

    // Update notification properties
    notification.time = time;
    notification.type = type;
    notification.scheduledAt = this.calculateScheduleTime(time, type);
    // Update interval for relative notifications so reactivation uses the same duration
    if (type === 'relative') {
      notification.interval = this.parseRelativeTimeToMs(time);
    } else {
      notification.interval = undefined;
    }
    notification.updatedAt = new Date();
    notification.enabled = true; // Re-enable when updating time

    // Schedule the updated notification
    if (Capacitor.isNativePlatform()) {
      try {
        await this.scheduleLocalNotification(id, notification.name, notification.scheduledAt);
      } catch (e) {
        console.error('Scheduling (update) failed', e);
      }
    }

    this.saveToStorage();
  }

  async reactivateNotification(id: string): Promise<void> {
    const notification = this.notifications.find(n => n.id === id);
    if (!notification) return;

    const newScheduledAt = this.calculateScheduleTime(notification.time, notification.type);
    await notificationLogger.logReactivate(id, notification.name, newScheduledAt.getTime());
    // Reuse update flow with existing time/type to compute next schedule from now
    await this.updateNotificationTime(id, notification.time, notification.type);
  }

  async refresh(): Promise<void> {
    await this.loadFromStorage();
  }

  getNotifications(): NotificationItem[] {
    return [...this.notifications].sort((a, b) => a.scheduledAt.getTime() - b.scheduledAt.getTime());
  }

  getSavedNames(): string[] {
    return [...this.savedNames];
  }

  private calculateScheduleTime(time: string, type: 'absolute' | 'relative'): Date {
    const now = new Date();

    if (type === 'absolute') {
      const [hours, minutes] = time.split(':').map(Number);
      const targetTime = new Date();
      targetTime.setHours(hours, minutes, 0, 0);

      // If the time has passed today, schedule for tomorrow
      if (targetTime <= now) {
        targetTime.setDate(targetTime.getDate() + 1);
      }

      return targetTime;
    } else {
      // Parse relative time like "15 minutes", "1 hour", "2 hours 30 minutes"
      const totalMs = this.parseRelativeTimeToMs(time);
      const targetTime = new Date(now.getTime() + totalMs);
      return targetTime;
    }
  }

  private addToSavedNames(name: string): void {
    if (!this.savedNames.includes(name)) {
      this.savedNames.unshift(name);
      // Keep only the most recent names
      this.savedNames = this.savedNames.slice(0, NOTIFICATION_CONFIG.MAX_SAVED_NAMES);
    } else {
      // Move to front if already exists
      this.savedNames = [name, ...this.savedNames.filter(n => n !== name)];
    }
  }

  private async saveToStorage(): Promise<void> {
    const notificationsJson = JSON.stringify(this.notifications);
    const savedNamesJson = JSON.stringify(this.savedNames);

    if (Capacitor.isNativePlatform()) {
      await Preferences.set({ key: 'notifications', value: notificationsJson });
      await Preferences.set({ key: 'savedNames', value: savedNamesJson });
      window.Android?.refreshWidget();
    } else {
      localStorage.setItem('notifications', notificationsJson);
      localStorage.setItem('savedNames', savedNamesJson);
    }
  }

  private async loadFromStorage(): Promise<void> {
    if (Capacitor.isNativePlatform()) {
      const { value: notificationsValue } = await Preferences.get({ key: 'notifications' });
      const { value: savedNamesValue } = await Preferences.get({ key: 'savedNames' });

      if (notificationsValue) {
        this.notifications = JSON.parse(notificationsValue).map((n: StoredNotification) => ({
          ...n,
          scheduledAt: new Date(n.scheduledAt),
          updatedAt: new Date(n.updatedAt || n.createdAt || new Date())
        }));
      }

      if (savedNamesValue) {
        this.savedNames = JSON.parse(savedNamesValue);
      }
    } else {
      const savedNotifications = localStorage.getItem('notifications');
      const savedNamesStorage = localStorage.getItem('savedNames');

      if (savedNotifications) {
        this.notifications = JSON.parse(savedNotifications).map((n: StoredNotification) => ({
          ...n,
          scheduledAt: new Date(n.scheduledAt),
          updatedAt: new Date(n.updatedAt || n.createdAt || new Date())
        }));
      }

      if (savedNamesStorage) {
        this.savedNames = JSON.parse(savedNamesStorage);
      }
    }
  }

  async verifyNotificationScheduled(id: string): Promise<boolean> {
  if (!Capacitor.isNativePlatform()) return true;
  
  try {
    const pending = await LocalNotifications.getPending();
    const numericId = toNumericId(id);
    
    const exists = pending.notifications.some(n => n.id === numericId);
    
    if (!exists) {
      console.warn(`⚠️ Notification ${id} not in pending list!`);
    }
    
    return exists;
  } catch (e) {
    console.error('Failed to verify notification', e);
    return false;
  }
}
}

export default NotificationService.getInstance();