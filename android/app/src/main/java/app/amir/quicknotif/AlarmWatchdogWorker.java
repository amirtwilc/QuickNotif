package app.amir.quicknotif;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * AlarmWatchdogWorker - periodic WorkManager task that detects and repairs missing alarms.
 *
 * Runs every 15 minutes (minimum WorkManager interval). For each enabled future notification,
 * it checks whether an AlarmManager alarm is still registered. If not (FLAG_NO_CREATE returns
 * null), it reschedules the alarm via NotifUtils.scheduleAlarm(). Already-alive alarms are
 * left untouched to avoid disrupting their trigger times.
 */
public class AlarmWatchdogWorker extends Worker {

    private static final String TAG = "AlarmWatchdog";

    public AlarmWatchdogWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppLogger.init(getApplicationContext());
        rescheduleOrphanedAlarms(getApplicationContext());
        return Result.success();
    }

    /**
     * Checks every enabled, future notification and reschedules any whose AlarmManager entry
     * is missing. Package-private to allow direct invocation from unit tests.
     */
    static void rescheduleOrphanedAlarms(Context ctx) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(ctx);

            if (notificationsJson.equals("[]")) {
                AppLogger.d(TAG, "📭 No notifications to check");
                return;
            }

            JSONArray array = new JSONArray(notificationsJson);

            long currentTime = System.currentTimeMillis();
            int rescheduled = 0;
            int alive = 0;
            int skipped = 0;

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject obj = array.getJSONObject(i);
                    boolean enabled = obj.optBoolean(NotifUtils.JSON_KEY_ENABLED, false);
                    String id = obj.optString(NotifUtils.JSON_KEY_ID, "");
                    long scheduledAt = NotifUtils.parseScheduledAt(obj);

                    // Skip disabled or already-expired notifications
                    if (!enabled || scheduledAt <= currentTime) {
                        skipped++;
                        continue;
                    }

                    // Use FLAG_NO_CREATE to probe whether the alarm still exists
                    Intent intent = new Intent(ctx, NotificationReceiver.class);
                    int numericId = NotifUtils.generateNumericId(id);
                    PendingIntent existing = PendingIntent.getBroadcast(
                            ctx,
                            numericId,
                            intent,
                            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                    );

                    if (existing == null) {
                        // Alarm was cleared by the OS - restore it
                        String name = obj.optString(NotifUtils.JSON_KEY_NAME, "");
                        NotifUtils.scheduleAlarm(ctx, id, name, scheduledAt);
                        rescheduled++;
                        AppLogger.w(TAG, "⚠️ Rescheduled missing alarm: " + name + " (ID: " + id + ")");
                    } else {
                        alive++;
                        AppLogger.d(TAG, "✅ Alarm alive: " + obj.optString(NotifUtils.JSON_KEY_NAME, "")
                                + " (ID: " + id + ")");
                    }

                } catch (Exception e) {
                    AppLogger.e(TAG, "❌ Error checking notification at index " + i, e);
                }
            }

            AppLogger.d(TAG, String.format("📊 Watchdog complete: %d rescheduled, %d alive, %d skipped",
                    rescheduled, alive, skipped));

        } catch (Exception e) {
            AppLogger.e(TAG, "❌ Watchdog failed", e);
        }
    }
}
