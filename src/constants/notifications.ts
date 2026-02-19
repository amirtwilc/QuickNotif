/**
 * Notification configuration constants
 * These values are shared between the notification service and Android native code
 * IMPORTANT: Changing CHANNEL_ID requires updating AndroidManifest.xml
 */

export const NOTIFICATION_CONFIG = {
  // Notification channel configuration
  CHANNEL_ID: 'timer-alerts',
  CHANNEL_NAME: 'Quick Notif',
  CHANNEL_DESCRIPTION: 'Critical timer notifications',
  CHANNEL_IMPORTANCE: 5, // Max importance for time-critical notifications
  CHANNEL_VISIBILITY: 1, // Public visibility

  // Visual styling
  ICON_COLOR: '#6366F1', // Indigo-500 from Tailwind
  SMALL_ICON: 'ic_stat_notification',
  LARGE_ICON: '',
  THREAD_ID: 'quick-notif',

  // Notification content
  TITLE: 'Quick Notif',

  // Behavior limits
  MAX_SAVED_NAMES: 10, // Maximum number of recent notification names to store
  MIN_SCHEDULE_DELAY_MS: 500, // Minimum time before scheduled notification (avoid immediate past)
  FALLBACK_DELAY_MS: 1000, // Delay to use when scheduled time is too soon
} as const;
