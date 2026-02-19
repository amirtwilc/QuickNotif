/**
 * Convert string ID to numeric ID for Android notifications
 * CRITICAL: This algorithm must match Java implementation in QuickNotifWidgetProvider.java
 * DO NOT MODIFY without updating Java side as well
 */
export function toNumericId(id: string): number {
  let hash = 5381;
  for (let i = 0; i < id.length; i++) {
    hash = ((hash << 5) + hash) ^ id.charCodeAt(i);
  }
  // Ensure positive 31-bit integer (Android requires non-zero int)
  const n = Math.abs(hash) % 2147483646 + 1;
  return n;
}
