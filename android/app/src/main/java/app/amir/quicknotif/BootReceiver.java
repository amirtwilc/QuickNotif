package app.amir.quicknotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * BootReceiver - Reschedules all active notifications after device reboot
 * Android cancels all scheduled alarms/notifications when the device reboots.
 * This receiver listens for BOOT_COMPLETED and reschedules all future notifications.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "QuickNotifBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "🔄 Device rebooted - Rescheduling notifications");
            rescheduleNotifications(context);
        }
    }

    private void rescheduleNotifications(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            String notificationsJson = prefs.getString("notifications", "[]");

            if (notificationsJson == null || notificationsJson.equals("[]")) {
                Log.d(TAG, "📭 No notifications to reschedule");
                return;
            }

            JSONArray array = new JSONArray(notificationsJson);
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            long currentTime = System.currentTimeMillis();
            int rescheduled = 0;
            int skipped = 0;

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject obj = array.getJSONObject(i);
                    boolean enabled = obj.optBoolean("enabled", false);
                    String id = obj.optString("id", "");

                    long scheduledAt = 0L;
                    try {
                        scheduledAt = obj.getLong("scheduledAt");
                    } catch (Exception e) {
                        String s = obj.optString("scheduledAt", null);
                        if (s != null && !s.isEmpty()) {
                            try {
                                Date parsed = isoFormat.parse(s);
                                if (parsed != null) scheduledAt = parsed.getTime();
                            } catch (Exception ignored) {}
                        }
                    }

                    // Only reschedule enabled future notifications
                    if (enabled && scheduledAt > currentTime) {
                        String name = obj.optString("name", "");

                        // Use Intent to launch MainActivity which will handle rescheduling
                        // This is more reliable than trying to use Capacitor plugins directly
                        Intent rescheduleIntent = new Intent(context, MainActivity.class);
                        rescheduleIntent.setAction("RESCHEDULE_NOTIFICATION");
                        rescheduleIntent.putExtra("notificationId", id);
                        rescheduleIntent.putExtra("notificationName", name);
                        rescheduleIntent.putExtra("scheduledAt", scheduledAt);
                        rescheduleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        context.startActivity(rescheduleIntent);

                        rescheduled++;

                        Log.d(TAG, String.format("✅ Queued reschedule: %s (ID: %s, at: %s)",
                                name, id, new Date(scheduledAt).toString()));
                    } else {
                        skipped++;

                        if (!enabled) {
                            Log.d(TAG, String.format("⏭️ Skipped disabled: %s", obj.optString("name", "")));
                        } else {
                            Log.d(TAG, String.format("⏭️ Skipped expired: %s", obj.optString("name", "")));
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