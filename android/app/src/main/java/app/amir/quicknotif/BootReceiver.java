package app.amir.quicknotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * BootReceiver — reschedules all active notifications after device reboot.
 *
 * Android cancels all AlarmManager alarms on reboot. This receiver listens for
 * BOOT_COMPLETED and calls NotifUtils.scheduleAlarm() directly for every enabled
 * future notification, without launching any Activity.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "QuickNotifBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Log.d(TAG, "🔄 Device rebooted - rescheduling notifications");
            rescheduleNotifications(context);
        }
    }

    private void rescheduleNotifications(Context context) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(context);

            if (notificationsJson.equals("[]")) {
                Log.d(TAG, "📭 No notifications to reschedule");
                return;
            }

            JSONArray array = new JSONArray(notificationsJson);

            // ISO date fallback parser for scheduledAt stored as a string
            SimpleDateFormat isoFormat = new SimpleDateFormat(NotifUtils.ISO_DATE_FORMAT, Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone(NotifUtils.UTC_TIMEZONE));

            long currentTime = System.currentTimeMillis();
            int rescheduled = 0;
            int skipped = 0;

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject obj = array.getJSONObject(i);
                    boolean enabled = obj.optBoolean(NotifUtils.JSON_KEY_ENABLED, false);
                    String id = obj.optString(NotifUtils.JSON_KEY_ID, "");

                    // scheduledAt is normally stored as a long. fall back to ISO string
                    long scheduledAt = 0L;
                    try {
                        scheduledAt = obj.getLong(NotifUtils.JSON_KEY_SCHEDULED_AT);
                    } catch (Exception e) {
                        String s = obj.optString(NotifUtils.JSON_KEY_SCHEDULED_AT, "");
                        if (!s.isEmpty()) {
                            try {
                                Date parsed = isoFormat.parse(s);
                                if (parsed != null) scheduledAt = parsed.getTime();
                            } catch (Exception ignored) {}
                        }
                    }

                    if (enabled && scheduledAt > currentTime) {
                        String name = obj.optString(NotifUtils.JSON_KEY_NAME, "");
                        NotifUtils.scheduleAlarm(context, id, name, scheduledAt);
                        rescheduled++;
                        Log.d(TAG, String.format("✅ Rescheduled: %s (ID: %s)", name, id));
                    } else {
                        skipped++;
                        if (!enabled) {
                            Log.d(TAG, "⏭️ Skipped disabled: " + obj.optString(NotifUtils.JSON_KEY_NAME, ""));
                        } else {
                            Log.d(TAG, "⏭️ Skipped expired: " + obj.optString(NotifUtils.JSON_KEY_NAME, ""));
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to process notification " + i, e);
                }
            }

            Log.d(TAG, String.format("📊 Boot reschedule complete: %d rescheduled, %d skipped",
                    rescheduled, skipped));

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to reschedule notifications after boot", e);
        }
    }
}
