package app.amir.quicknotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;


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

        AppLogger.init(context);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            AppLogger.d(TAG, "🔄 Device rebooted - rescheduling notifications");
            rescheduleNotifications(context);
        }
    }

    private void rescheduleNotifications(Context context) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(context);

            if (notificationsJson.equals("[]")) {
                AppLogger.d(TAG,"📭 No notifications to reschedule");
                return;
            }

            JSONArray array = new JSONArray(notificationsJson);

            long currentTime = System.currentTimeMillis();
            int rescheduled = 0;
            int skipped = 0;

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject obj = array.getJSONObject(i);
                    boolean enabled = obj.optBoolean(NotifUtils.JSON_KEY_ENABLED, false);
                    String id = obj.optString(NotifUtils.JSON_KEY_ID, "");

                    long scheduledAt = NotifUtils.parseScheduledAt(obj);

                    if (enabled && scheduledAt > currentTime) {
                        String name = obj.optString(NotifUtils.JSON_KEY_NAME, "");
                        NotifUtils.scheduleAlarm(context, id, name, scheduledAt);
                        rescheduled++;
                        AppLogger.d(TAG,String.format("✅ Rescheduled: %s (ID: %s)", name, id));
                    } else {
                        skipped++;
                        if (!enabled) {
                            AppLogger.d(TAG,"⏭️ Skipped disabled: " + obj.optString(NotifUtils.JSON_KEY_NAME, ""));
                        } else {
                            AppLogger.d(TAG,"⏭️ Skipped expired: " + obj.optString(NotifUtils.JSON_KEY_NAME, ""));
                        }
                    }

                } catch (Exception e) {
                    AppLogger.e(TAG,"❌ Failed to process notification " + i, e);
                }
            }

            AppLogger.d(TAG,String.format("📊 Boot reschedule complete: %d rescheduled, %d skipped",
                    rescheduled, skipped));

        } catch (Exception e) {
            AppLogger.e(TAG,"❌ Failed to reschedule notifications after boot", e);
        }
    }
}
