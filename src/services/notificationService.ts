import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';

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

export class NotificationService {
  private static instance: NotificationService;
  private notifications: NotificationItem[] = [];
  private savedNames: string[] = [];

  // Stable numeric ID generator to avoid collisions
  private toNumericId(id: string): number {
    let hash = 5381;
    for (let i = 0; i < id.length; i++) {
      hash = ((hash << 5) + hash) ^ id.charCodeAt(i);
    }
    // Ensure positive 31-bit integer (Android requires non-zero int)
    const n = Math.abs(hash) % 2147483646 + 1;
    return n;
  }

  static getInstance(): NotificationService {
    if (!NotificationService.instance) {
      NotificationService.instance = new NotificationService();
    }
    return NotificationService.instance;
  }

  async initialize() {
    if (Capacitor.isNativePlatform()) {
      // Request all necessary permissions for background notifications
      const permission = await LocalNotifications.requestPermissions();
      if (permission.display !== 'granted') {
        throw new Error('Notification permission not granted');
      }

      // Create notification channel for Android (required for background notifications)
      await LocalNotifications.createChannel({
        id: 'timer-alerts',
        name: 'Quick Notif',
        description: 'Critical timer notifications that bypass battery optimization',
        importance: 5, // Maximum importance for immediate delivery
        visibility: 1, // Public visibility
        vibration: true,
        lights: true,
        lightColor: '#6366F1'
      });
    }
    
    await this.loadFromStorage();
  }

  async scheduleNotification(name: string, time: string, type: 'absolute' | 'relative'): Promise<string> {
    const id = `notification_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const scheduledAt = this.calculateScheduleTime(time, type);
    
    // Store interval (ms) for relative notifications so the widget can reactivate correctly
    const intervalMs = type === 'relative' ? (() => {
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
    })() : undefined;
    
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
    
    if (Capacitor.isNativePlatform()) {
        const atDate = scheduledAt.getTime() <= Date.now() + 500 ? new Date(Date.now() + 1000) : scheduledAt;
        try {
          await LocalNotifications.schedule({
            notifications: [{
              title: 'Quick Notif',
              body: name,
              id: this.toNumericId(id),
              schedule: { 
                at: atDate,
                allowWhileIdle: true,
                repeats: false
              },
              channelId: 'timer-alerts',
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
              smallIcon: 'ic_stat_notification',
              largeIcon: '',
              iconColor: '#6366F1',
              threadIdentifier: 'quick-notif'
            }]
          });
        } catch (e) {
          console.error('Scheduling notification failed', e);
        }
    }

    this.saveToStorage();
    return id;
  }

  async toggleNotification(id: string): Promise<void> {
    const notification = this.notifications.find(n => n.id === id);
    if (!notification) return;

    notification.enabled = !notification.enabled;
    
    if (Capacitor.isNativePlatform()) {
      const numericId = this.toNumericId(id);
      
      if (notification.enabled) {
        const atDate = notification.scheduledAt.getTime() <= Date.now()
          ? this.calculateScheduleTime(notification.time, notification.type)
          : notification.scheduledAt;
        notification.scheduledAt = atDate;
        try {
          await LocalNotifications.schedule({
            notifications: [{
              title: 'Quick Notif',
              body: notification.name,
              id: numericId,
              schedule: { 
                at: atDate,
                allowWhileIdle: true,
                repeats: false
              },
              channelId: 'timer-alerts',
              attachments: undefined,
              actionTypeId: '',
              extra: { 
                wakeUp: true,
                exactTiming: true 
              },
              ongoing: false,
              autoCancel: true,
              largeBody: notification.name,
              summaryText: '',
              smallIcon: 'ic_stat_notification',
              largeIcon: '',
              iconColor: '#6366F1',
              threadIdentifier: 'quick-notif'
            }]
          });
        } catch (e) {
          console.error('Scheduling (toggle) failed', e);
        }
      } else {
        await LocalNotifications.cancel({ notifications: [{ id: numericId }] });
      }
    }

    this.saveToStorage();
  }

  async deleteNotification(id: string): Promise<void> {
    if (Capacitor.isNativePlatform()) {
      const numericId = this.toNumericId(id);
      await LocalNotifications.cancel({ notifications: [{ id: numericId }] });
    }
    
    this.notifications = this.notifications.filter(n => n.id !== id);
    this.saveToStorage();
  }

  async updateNotificationTime(id: string, time: string, type: 'absolute' | 'relative'): Promise<void> {
    const notification = this.notifications.find(n => n.id === id);
    if (!notification) return;

    // Cancel existing notification if it exists
    if (Capacitor.isNativePlatform()) {
      const numericId = parseInt(id.replace(/[^0-9]/g, '').slice(0, 8));
      await LocalNotifications.cancel({ notifications: [{ id: numericId }] });
    }

    // Update notification properties
    notification.time = time;
    notification.type = type;
    notification.scheduledAt = this.calculateScheduleTime(time, type);
    // Update interval for relative notifications so reactivation uses the same duration
    if (type === 'relative') {
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
      notification.interval = totalMinutes * 60 * 1000;
    } else {
      notification.interval = undefined;
    }
    notification.updatedAt = new Date();
    notification.enabled = true; // Re-enable when updating time

    // Schedule the updated notification
    if (Capacitor.isNativePlatform()) {
      const numericId = this.toNumericId(id);
      const atDate = notification.scheduledAt.getTime() <= Date.now() + 500
        ? new Date(Date.now() + 1000)
        : notification.scheduledAt;
      try {
        await LocalNotifications.schedule({
          notifications: [{
            title: 'Quick Notif',
            body: notification.name,
            id: numericId,
            schedule: { 
              at: atDate,
              allowWhileIdle: true,
              repeats: false
            },
            channelId: 'timer-alerts',
            attachments: undefined,
            actionTypeId: '',
            extra: { 
              wakeUp: true,
              exactTiming: true 
            },
            ongoing: false,
            autoCancel: true,
            largeBody: notification.name,
            summaryText: '',
            smallIcon: 'ic_stat_notification',
            largeIcon: '',
            iconColor: '#6366F1',
            threadIdentifier: 'quick-notif'
          }]
        });
      } catch (e) {
        console.error('Scheduling (update) failed', e);
      }
    }

    this.saveToStorage();
  }

  async reactivateNotification(id: string): Promise<void> {
    const notification = this.notifications.find(n => n.id === id);
    if (!notification) return;
    // Reuse update flow with existing time/type to compute next schedule from now
    await this.updateNotificationTime(id, notification.time, notification.type);
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
      const parts = time.toLowerCase().split(' ');
      let totalMinutes = 0;
      
      for (let i = 0; i < parts.length; i += 2) {
        const value = parseInt(parts[i]);
        const unit = parts[i + 1];
        
        if (unit.includes('hour')) {
          totalMinutes += value * 60;
        } else if (unit.includes('minute')) {
          totalMinutes += value;
        }
      }
      
      const targetTime = new Date(now.getTime() + totalMinutes * 60 * 1000);
      return targetTime;
    }
  }

  private addToSavedNames(name: string): void {
    if (!this.savedNames.includes(name)) {
      this.savedNames.unshift(name);
      // Keep only the 10 most recent names
      this.savedNames = this.savedNames.slice(0, 10);
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
        this.notifications = JSON.parse(notificationsValue).map((n: any) => ({
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
        this.notifications = JSON.parse(savedNotifications).map((n: any) => ({
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
}
