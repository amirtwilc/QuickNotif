// src/services/notificationLogger.ts
import { Capacitor } from '@capacitor/core';
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import { toNumericId } from '@/utils/notificationUtils';
import type { NotificationItem } from './notificationService';

export interface LogEntry {
  timestamp: string;
  type: 'SCHEDULE' | 'VERIFY' | 'FIRE' | 'DELETE' | 'REACTIVATE' | 'SYSTEM_CHECK' | 'ERROR' | 'BOOT';
  notificationId?: string;
  notificationName?: string;
  scheduledAt?: number;
  message: string;
  androidPendingCount?: number;
  appNotificationCount?: number;
  details?: Record<string, unknown>;
}

class NotificationLogger {
  private static instance: NotificationLogger;
  private logBuffer: LogEntry[] = [];
  private DEBUG_MODE = true; // Production mode
  private intervalId: number | null = null;
  private LOG_FILE = 'notification_debug.log';
  private CHECK_INTERVAL = 60 * 1000; // 1 minute
  private MAX_LOG_SIZE = 10000; // Maximum lines before rotation

  private constructor() {
    if (this.DEBUG_MODE && Capacitor.isNativePlatform()) {
      this.initializeLogger();
    }
  }

  static getInstance(): NotificationLogger {
    if (!NotificationLogger.instance) {
      NotificationLogger.instance = new NotificationLogger();
    }
    return NotificationLogger.instance;
  }

  private async initializeLogger() {
    try {
      console.log('🚀 Initializing NotificationLogger...');
      console.log('  - DEBUG_MODE:', this.DEBUG_MODE);
      console.log('  - Platform:', Capacitor.getPlatform());
      console.log('  - isNativePlatform:', Capacitor.isNativePlatform());
      
      await this.log({
        timestamp: new Date().toISOString(),
        type: 'SYSTEM_CHECK',
        message: '🚀 NotificationLogger initialized'
      });

      // Start periodic system checks
      this.startPeriodicChecks();

      console.log('✅ NotificationLogger: Debug logging enabled');
    } catch (e) {
      console.error('❌ Failed to initialize logger', e);
    }
  }

  async log(entry: LogEntry) {
    if (!this.DEBUG_MODE || !Capacitor.isNativePlatform()) {
      console.log('⚠️ Logging skipped:', { DEBUG_MODE: this.DEBUG_MODE, isNative: Capacitor.isNativePlatform() });
      return;
    }

    try {
      console.log('📝 Logging entry:', entry.type, entry.message);
      
      // Add to buffer
      this.logBuffer.push(entry);

      // Format log line
      const logLine = this.formatLogEntry(entry);
      console.log('📄 Formatted line:', logLine.substring(0, 100) + '...');

      // Append to file
      await this.appendToLog(logLine);
      console.log('✅ Log written successfully');

      // Keep buffer size manageable
      if (this.logBuffer.length > 100) {
        this.logBuffer.shift();
      }

    } catch (e) {
      console.error('❌ Failed to write log', e);
    }
  }

  private formatLogEntry(entry: LogEntry): string {
    const parts = [
      `[${entry.timestamp}]`,
      `[${entry.type}]`
    ];

    if (entry.notificationId) {
      parts.push(`[ID:${entry.notificationId}]`);
    }

    if (entry.notificationName) {
      parts.push(`[${entry.notificationName}]`);
    }

    parts.push(entry.message);

    if (entry.androidPendingCount !== undefined) {
      parts.push(`[Android:${entry.androidPendingCount}]`);
    }

    if (entry.appNotificationCount !== undefined) {
      parts.push(`[App:${entry.appNotificationCount}]`);
    }

    if (entry.details) {
      parts.push(`[Details:${JSON.stringify(entry.details)}]`);
    }

    return parts.join(' ') + '\n';
  }

  private async appendToLog(line: string) {
    try {
      // Check if file exists
      let existingContent = '';
      try {
        const result = await Filesystem.readFile({
          path: this.LOG_FILE,
          directory: Directory.External,
          encoding: Encoding.UTF8
        });
        existingContent = result.data as string;
      } catch (e) {
        // File doesn't exist yet, that's OK
        console.log('Log file does not exist yet, will create');
      }

      // Check if log is too large and rotate
      const lines = existingContent.split('\n');
      if (lines.length > this.MAX_LOG_SIZE) {
        // Keep last 1000 lines
        existingContent = lines.slice(-1000).join('\n') + '\n';
        console.log('Log rotated - keeping last 1000 lines');
      }

      // Append new line
      await Filesystem.writeFile({
        path: this.LOG_FILE,
        data: existingContent + line,
        directory: Directory.External,
        encoding: Encoding.UTF8,
        recursive: true
      });

    } catch (e) {
      console.error('Failed to append to log file', e);
    }
  }

  async logSchedule(id: string, name: string, scheduledAt: number, type: string) {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'SCHEDULE',
      notificationId: id,
      notificationName: name,
      scheduledAt,
      message: `📅 Scheduled ${type} notification for ${new Date(scheduledAt).toLocaleString()}`
    });
  }

  async logVerification(id: string, name: string, exists: boolean, androidCount: number) {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'VERIFY',
      notificationId: id,
      notificationName: name,
      androidPendingCount: androidCount,
      message: exists ? '✅ Verified in Android' : '❌ NOT found in Android pending list'
    });
  }

  async logFire(id: string, name: string) {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'FIRE',
      notificationId: id,
      notificationName: name,
      message: '🔔 Notification fired'
    });
  }

  async logDelete(id: string, name: string) {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'DELETE',
      notificationId: id,
      notificationName: name,
      message: '🗑️ Notification deleted'
    });
  }

  async logReactivate(id: string, name: string, newScheduledAt: number) {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'REACTIVATE',
      notificationId: id,
      notificationName: name,
      scheduledAt: newScheduledAt,
      message: `🔄 Reactivated for ${new Date(newScheduledAt).toLocaleString()}`
    });
  }

  async logError(message: string, error: unknown, notificationId?: string) {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'ERROR',
      notificationId,
      message: `❌ ERROR: ${message}`,
      details: {
        error: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined
      }
    });
  }

  async logBoot() {
    await this.log({
      timestamp: new Date().toISOString(),
      type: 'BOOT',
      message: '🔄 Device rebooted - Checking notifications'
    });
  }

  async logSystemCheck(appCount: number, androidCount: number, orphaned: string[], missing: number[], allAppIds: string[], allAndroidIds: number[]) {

      let orphanedDetails: string[] = [];
      let missingDetails: string[] = [];
      let allAppIdsWithNames: string[] = [];
      let allAndroidIdsWithNames: string[] = [];

      try {
          const { default: notificationService } = await import('./notificationService');
          const allNotifications = notificationService.getNotifications();

          // Format orphaned notifications
          orphanedDetails = orphaned.map(id => {
              const notif = allNotifications.find((n: NotificationItem) => n.id === id);
              return notif ? `${notif.name || 'Unnamed'} (${id})` : id;
          });

          // Format missing notifications (numeric IDs)
          missingDetails = missing.map(numericId => {
              // Find notification by converting string IDs to numeric and matching
              const notif = allNotifications.find((n: NotificationItem) => toNumericId(n.id) === numericId);
              return notif ? `${notif.name || 'Unnamed'} (${numericId})` : `Unknown (${numericId})`;
          });

          // Format allAppIds with names
          allAppIdsWithNames = allAppIds.map(id => {
              const notif = allNotifications.find((n: NotificationItem) => n.id === id);
              const name = notif ? (notif.name || 'Unnamed') : 'Unknown';
              return `${id} (${name})`;
          });

          // Format allAndroidIds with names
          allAndroidIdsWithNames = allAndroidIds.map(numericId => {
              const notif = allNotifications.find((n: NotificationItem) => toNumericId(n.id) === numericId);
              const name = notif ? (notif.name || 'Unnamed') : 'Unknown';
              return `${numericId} (${name})`;
          });

      } catch (e) {
          // Fallback: just use IDs if import fails
          orphanedDetails = orphaned.map(id => id);
          missingDetails = missing.map(id => `${id}`);
          allAppIdsWithNames = allAppIds.map(id => id);
          allAndroidIdsWithNames = allAndroidIds.map(id => `${id}`);
          console.error('Failed to get notification names:', e);
      }

    await this.log({
      timestamp: new Date().toISOString(),
      type: 'SYSTEM_CHECK',
      appNotificationCount: appCount,
      androidPendingCount: androidCount,
      message: orphanedDetails.length > 0
        ? `📊 System check - ⚠️ ${orphanedDetails.length} orphaned: ${orphanedDetails.join(', ')}`
        : `📊 System check complete`,
      details: {
        synced: appCount - orphaned.length,
        orphanedInApp: orphaned.length,
        missingInApp: missing.length,
        orphanedNotifications: orphanedDetails,
        missingNotifications: missingDetails,
        allAppIds: allAppIdsWithNames,
        allAndroidIds: allAndroidIdsWithNames
      }
    });
  }

  // Manual trigger for system check (for debug button)
  async triggerSystemCheck(): Promise<void> {
    console.log('🔍 Manual system check triggered');
    await this.performSystemCheck();
  }

  private startPeriodicChecks() {
    if (this.intervalId) return; // Already started

    // Check every minute
    this.intervalId = window.setInterval(() => {
      this.performSystemCheck();
    }, this.CHECK_INTERVAL);

    // Initial check
    setTimeout(() => this.performSystemCheck(), 5000); // 5 seconds after start
  }

  stopPeriodicChecks() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  private async performSystemCheck() {
    if (!Capacitor.isNativePlatform()) return;

    try {
      // Import here to avoid circular dependency
      const { LocalNotifications } = await import('@capacitor/local-notifications');

      // Get plugin-scheduled notifications
      const pending = await LocalNotifications.getPending();
      const pluginScheduledIds = pending.notifications.map(n => n.id);

      // Get app's notifications from storage
      const { default: notificationService } = await import('./notificationService');
      const appNotifications = notificationService.getNotifications();

      // Collect all app IDs and numeric IDs
      const allAppIds: string[] = [];
      const allNumericIds: number[] = [];

      for (const appNotif of appNotifications) {
        if (appNotif.enabled && appNotif.scheduledAt.getTime() > Date.now()) {
          allAppIds.push(appNotif.id);
          allNumericIds.push(toNumericId(appNotif.id));
        }
      }

      // Check AlarmManager-scheduled notifications (native bridge)
      let alarmScheduledIds: number[] = [];
      if (window.Android?.checkAllAlarms) {
        try {
          const result = window.Android.checkAllAlarms(JSON.stringify(allNumericIds));
          alarmScheduledIds = JSON.parse(result);
          console.log(`🔍 AlarmManager check: ${alarmScheduledIds.length} alarms scheduled`);
        } catch (e) {
          console.error('Failed to check AlarmManager alarms:', e);
        }
      } else {
        console.warn('AlarmManager verification not available - using plugin-only check');
      }

      // Combine both sources (plugin + AlarmManager)
      const allScheduledIds = [...new Set([...pluginScheduledIds, ...alarmScheduledIds])];

      // Find orphaned: in app storage but NOT scheduled anywhere
      const orphaned: string[] = [];
      for (const appNotif of appNotifications) {
        if (appNotif.enabled && appNotif.scheduledAt.getTime() > Date.now()) {
          const numericId = toNumericId(appNotif.id);
          const isScheduled = allScheduledIds.includes(numericId);

          if (!isScheduled) {
            orphaned.push(appNotif.id);  // REAL orphaned - not in plugin OR AlarmManager!
          }
        }
      }

      // Find missing: scheduled in Android but not in app
      const missing: number[] = [];
      for (const scheduledId of allScheduledIds) {
        const inApp = allNumericIds.includes(scheduledId);
        if (!inApp) {
          missing.push(scheduledId);
        }
      }

      await this.logSystemCheck(
        allAppIds.length,  // Fixed: use pre-calculated count
        allScheduledIds.length,  // Total actually scheduled
        orphaned,
        missing,
        allAppIds,
        allScheduledIds  // Now includes both plugin and AlarmManager
      );

      // Warn about REAL orphaned notifications and auto-reschedule them
      if (orphaned.length > 0) {
        console.error(`❌ Found ${orphaned.length} ORPHANED notifications — auto-rescheduling`);
        await notificationService.rescheduleOrphans(orphaned);
      }

    } catch (e) {
      await this.logError('System check failed', e);
    }
  }

  // Get the log file for sharing/viewing
  async getLogFile(): Promise<string> {
    if (!Capacitor.isNativePlatform()) {
      return 'Debug logging only available on mobile';
    }

    try {
      const result = await Filesystem.readFile({
        path: this.LOG_FILE,
        directory: Directory.External,
        encoding: Encoding.UTF8
      });
      return result.data as string;
    } catch (e) {
      console.error('Error reading log file:', e);
      return `Error reading log file: ${e}`;
    }
  }

  // Get log file URI for sharing
  async getLogFileUri(): Promise<string> {
    if (!Capacitor.isNativePlatform()) {
      throw new Error('Not on native platform');
    }

    try {
      const result = await Filesystem.getUri({
        path: this.LOG_FILE,
        directory: Directory.Documents
      });
      return result.uri;
    } catch (e) {
      throw new Error(`Failed to get log file URI: ${e}`);
    }
  }

  // Clear the log file
  async clearLog() {
    if (!Capacitor.isNativePlatform()) return;

    try {
      await Filesystem.writeFile({
        path: this.LOG_FILE,
        data: `[${new Date().toISOString()}] [SYSTEM_CHECK] 🗑️ Log cleared\n`,
        directory: Directory.External,
        encoding: Encoding.UTF8,
        recursive: true
      });

      this.logBuffer = [];
      console.log('✅ Log file cleared');
    } catch (e) {
      console.error('Failed to clear log file', e);
    }
  }

  // Get statistics
  async getStatistics() {
    try {
      const logContent = await this.getLogFile();
      const lines = logContent.split('\n');

      const stats = {
        totalEntries: lines.length - 1,
        schedules: lines.filter(l => l.includes('[SCHEDULE]')).length,
        verifications: lines.filter(l => l.includes('[VERIFY]')).length,
        fires: lines.filter(l => l.includes('[FIRE]')).length,
        deletes: lines.filter(l => l.includes('[DELETE]')).length,
        errors: lines.filter(l => l.includes('[ERROR]')).length,
        systemChecks: lines.filter(l => l.includes('[SYSTEM_CHECK]')).length,
        verificationFailures: lines.filter(l => l.includes('❌ NOT found in Android')).length,
        orphanedNotifications: lines.filter(l => l.includes('orphanedInApp')).length
      };

      return stats;
    } catch (e) {
      return null;
    }
  }

  // Check if debug mode is enabled
  isDebugMode(): boolean {
    return this.DEBUG_MODE;
  }

  // Set check interval (in milliseconds)
  setCheckInterval(ms: number) {
    this.CHECK_INTERVAL = ms;
    
    // Restart checks with new interval
    this.stopPeriodicChecks();
    if (this.DEBUG_MODE) {
      this.startPeriodicChecks();
    }
  }
}

// Export singleton instance
export default NotificationLogger.getInstance();