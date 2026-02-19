package app.amir.quicknotif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared constants and utility methods used across widget, receiver, and activity classes.
 */
public final class NotifUtils {

    private static final String TAG = "NotifUtils";

    // SharedPreferences
    public static final String PREFS_NAME        = "CapacitorStorage";
    public static final String KEY_NOTIFICATIONS = "notifications";

    // Notification channel
    public static final String CHANNEL_ID   = "timer-alerts";
    public static final String CHANNEL_NAME = "Quick Notif";
    public static final int    ACCENT_COLOR = 0xFF6366F1;

    // Logging
    public static final String LOG_FILE_NAME = "notification_debug.log";

    private NotifUtils() {}

    // ─── ID generation ───────────────────────────────────────────────────────

    /**
     * DJB2-variant hash: converts a string notification ID to a stable positive int.
     * MUST stay identical to toNumericId() in notificationService.ts.
     */
    public static int generateNumericId(String stringId) {
        int hash = 5381;
        for (int i = 0; i < stringId.length(); i++) {
            hash = ((hash << 5) + hash) ^ stringId.charAt(i);
        }
        return Math.abs(hash) % 2147483646 + 1;
    }

    // ─── SharedPreferences helpers ────────────────────────────────────────────

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String readNotificationsJson(Context context) {
        return getPrefs(context).getString(KEY_NOTIFICATIONS, "[]");
    }

    public static void saveNotificationsJson(Context context, String json) {
        getPrefs(context).edit().putString(KEY_NOTIFICATIONS, json).apply();
    }

    // ─── AlarmManager helpers ─────────────────────────────────────────────────

    /**
     * Schedule an exact alarm for the given notification.
     * Cancels any existing alarm for the same ID first to prevent duplicates.
     */
    public static void scheduleAlarm(Context context, String id, String name, long scheduledAt) {
        try {
            Log.d(TAG, "📅 Scheduling alarm: " + name + " at " + new Date(scheduledAt));

            Intent notificationIntent = new Intent(context, NotificationReceiver.class);
            notificationIntent.putExtra("notificationId", id);
            notificationIntent.putExtra("notificationName", name);

            int numericId = generateNumericId(id);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    numericId,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledAt,
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            scheduledAt,
                            pendingIntent
                    );
                }
                Log.d(TAG, "✅ Alarm scheduled in AlarmManager");
            } else {
                Log.e(TAG, "❌ AlarmManager is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to schedule alarm", e);
        }
    }

    /** Cancel an existing alarm by string notification ID. */
    public static void cancelAlarm(Context context, String id) {
        try {
            Intent notificationIntent = new Intent(context, NotificationReceiver.class);
            notificationIntent.putExtra("notificationId", id);
            notificationIntent.putExtra("notificationName", "");

            int numericId = generateNumericId(id);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    numericId,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d(TAG, "✅ Alarm canceled for ID: " + id);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to cancel alarm", e);
        }
    }

    // ─── Logging ──────────────────────────────────────────────────────────────

    /**
     * Append a line to the debug log file in Documents/.
     * Pass scheduledAt=0 when there is no scheduled time to report.
     * Uses try-with-resources so the FileWriter is always closed.
     */
    public static void writeToLog(Context context, String type, String id,
                                  String name, long scheduledAt) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            String timestamp = sdf.format(new Date());

            String scheduledStr = scheduledAt > 0
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(scheduledAt))
                    : "N/A";

            String logLine = String.format("[%s] [%s] [ID:%s...] [%s] for %s\n",
                    timestamp,
                    type,
                    id != null ? id.substring(0, Math.min(12, id.length())) : "unknown",
                    name != null ? name : "Unnamed",
                    scheduledStr
            );

            File documentsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);
            File logFile = new File(documentsDir, LOG_FILE_NAME);

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logLine);
            }

            Log.d(TAG, "✅ Wrote to log file");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to write to log", e);
        }
    }

    // ─── Widget refresh ───────────────────────────────────────────────────────

    /** Notify all active widget instances to refresh their list view. */
    public static void refreshAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, QuickNotifWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
            QuickNotifWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
