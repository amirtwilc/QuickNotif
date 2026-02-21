package app.amir.quicknotif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * AppWidgetProvider for the QuickNotif home screen widget.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Lifecycle — starts the periodic AlarmManager refresh tick when the first widget
 *       instance is added ({@link #onEnabled}), and cancels it when the last is removed
 *       ({@link #onDisabled}).</li>
 *   <li>Rendering — inflates {@link RemoteViews}, connects the list to
 *       {@link QuickNotifWidgetService}, and wires button click intents
 *       in {@link #updateAppWidget}.</li>
 *   <li>Action dispatch — handles DELETE, REACTIVATE, RESCHEDULE, ADD, and REFRESH
 *       broadcasts in {@link #onReceive}, sent by list-item buttons and the widget
 *       top-bar buttons.</li>
 * </ul>
 *
 * <p>All widget instances share a single periodic refresh alarm identified by
 * {@link #REQUEST_CODE_PERIODIC_UPDATE}, firing every {@link #WIDGET_REFRESH_INTERVAL_MS}
 * milliseconds to keep displayed notification statuses current.
 */
public class QuickNotifWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "QuickNotifWidget";
    private static final int    REQUEST_CODE_PERIODIC_UPDATE = 1001;
    private static final long   WIDGET_REFRESH_INTERVAL_MS   = 60 * 1000L; // Every minute

    public static final String ACTION_REFRESH    = "app.amir.quicknotif.ACTION_REFRESH";
    public static final String ACTION_ALARM_TICK = "app.amir.quicknotif.ACTION_ALARM_TICK";
    public static final String ACTION_DELETE     = "app.amir.quicknotif.ACTION_DELETE";
    public static final String ACTION_REACTIVATE = "app.amir.quicknotif.ACTION_REACTIVATE";
    public static final String ACTION_RESCHEDULE = "app.amir.quicknotif.ACTION_RESCHEDULE";
    public static final String ACTION_ADD        = "app.amir.quicknotif.ACTION_ADD";

    @Override
    public void onEnabled(Context context) {
        schedulePeriodicUpdate(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelPeriodicUpdate(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        schedulePeriodicUpdate(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        AppLogger.init(context);
        super.onReceive(context, intent);
        String action = intent.getAction();

        if (ACTION_DELETE.equals(action)) {
            String notificationId = intent.getStringExtra(NotifUtils.EXTRA_NOTIFICATION_ID);
            if (notificationId != null) {
                deleteNotification(context, notificationId);
                NotifUtils.refreshAllWidgets(context);
            }
        } else if (ACTION_REACTIVATE.equals(action)) {
            String notificationId = intent.getStringExtra(NotifUtils.EXTRA_NOTIFICATION_ID);
            if (notificationId != null) {
                reactivateNotification(context, notificationId);
                NotifUtils.refreshAllWidgets(context);
            }
        } else if (ACTION_RESCHEDULE.equals(action)) {
            String notificationId = intent.getStringExtra(NotifUtils.EXTRA_NOTIFICATION_ID);
            String notificationName = intent.getStringExtra(NotifUtils.EXTRA_NOTIFICATION_NAME);
            String notificationType = intent.getStringExtra(NotifUtils.EXTRA_NOTIFICATION_TYPE);
            if (notificationId != null) {
                Intent rescheduleIntent = new Intent(context, RescheduleActivity.class);
                rescheduleIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_ID, notificationId);
                rescheduleIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_NAME, notificationName);
                rescheduleIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_TYPE, notificationType);
                rescheduleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(rescheduleIntent);
            }
        } else if (ACTION_ADD.equals(action)) {
            Intent addIntent = new Intent(context, AddNotificationActivity.class);
            addIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(addIntent);
        } else if (ACTION_REFRESH.equals(action) || ACTION_ALARM_TICK.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            NotifUtils.refreshAllWidgets(context);
        }
    }

    private void deleteNotification(Context context, String notificationId) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(context);
            JSONArray array = new JSONArray(notificationsJson);
            JSONArray newArray = new JSONArray();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!notificationId.equals(obj.optString(NotifUtils.JSON_KEY_ID, ""))) {
                    newArray.put(obj);
                }
            }

            NotifUtils.saveNotificationsJson(context, newArray.toString());
        } catch (Exception e) {
            AppLogger.e(TAG,"❌ Failed to delete notification: " + notificationId, e);
        }
    }

    private void reactivateNotification(Context context, String notificationId) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(context);
            JSONArray array = new JSONArray(notificationsJson);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.optString(NotifUtils.JSON_KEY_ID, "");

                if (id.equals(notificationId)) {
                    String name = obj.optString(NotifUtils.JSON_KEY_NAME, "");
                    String time = obj.optString(NotifUtils.JSON_KEY_TIME, "");
                    String type = obj.optString(NotifUtils.JSON_KEY_TYPE, "");

                    // Heuristic: infer type if missing or invalid
                    if (!NotifUtils.TYPE_RELATIVE.equals(type) && !NotifUtils.TYPE_ABSOLUTE.equals(type)) {
                        String lt = time.toLowerCase(Locale.getDefault());
                        if (lt.contains("hour") || lt.contains("minute")) {
                            type = NotifUtils.TYPE_RELATIVE;
                        } else if (lt.contains(":")) {
                            type = NotifUtils.TYPE_ABSOLUTE;
                        } else {
                            type = NotifUtils.TYPE_RELATIVE;
                        }
                        obj.put(NotifUtils.JSON_KEY_TYPE, type);
                    }

                    long interval = 0L;
                    if (NotifUtils.TYPE_RELATIVE.equals(type)) {
                        interval = obj.optLong(NotifUtils.JSON_KEY_INTERVAL, 0L);
                        if (interval <= 0 && !time.isEmpty()) {
                            interval = parseRelativeIntervalMs(time);
                            if (interval > 0) {
                                obj.put(NotifUtils.JSON_KEY_INTERVAL, interval);
                            }
                        }
                    }

                    long newScheduledAt = calculateNewScheduleTime(type, time, interval);
                    if (newScheduledAt <= 0) {
                        AppLogger.e(TAG,"❌ Could not calculate schedule time for: " + name);
                        break;
                    }

                    obj.put(NotifUtils.JSON_KEY_SCHEDULED_AT, newScheduledAt);
                    obj.put(NotifUtils.JSON_KEY_UPDATED_AT, System.currentTimeMillis());
                    obj.put(NotifUtils.JSON_KEY_ENABLED, true);

                    NotifUtils.saveNotificationsJson(context, array.toString());
                    NotifUtils.scheduleAlarm(context, id, name, newScheduledAt);
                    NotifUtils.writeToLog(context, "REACTIVATE", id, name, newScheduledAt);

                    break;
                }
            }
        } catch (Exception e) {
            AppLogger.e(TAG,"❌ Failed to reactivate notification: " + notificationId, e);
        }
    }

    /**
     * Calculates the next trigger timestamp for a notification.
     *
     * @return millisecond timestamp, or -1 if the time cannot be determined.
     */
    private long calculateNewScheduleTime(String type, String time, long interval) {
        try {
            if (NotifUtils.TYPE_RELATIVE.equals(type)) {
                if (interval > 0) {
                    return System.currentTimeMillis() + interval;
                }
                if (!time.isEmpty()) {
                    long ms = parseRelativeIntervalMs(time);
                    if (ms > 0) return System.currentTimeMillis() + ms;
                }
            } else {
                // Absolute: schedule for the next occurrence of the given HH:mm time
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date timeDate = timeFormat.parse(time);

                if (timeDate != null) {
                    Calendar timeCal = Calendar.getInstance();
                    timeCal.setTime(timeDate);

                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                    cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                    }

                    return cal.getTimeInMillis();
                }
            }
        } catch (Exception e) {
            AppLogger.e(TAG,"❌ Failed to calculate schedule time", e);
        }

        return -1L;
    }

    /**
     * Parses a duration string (e.g. "1 hour 30 minutes") into milliseconds.
     *
     * @return duration in milliseconds, or 0 if the string cannot be parsed.
     */
    private static long parseRelativeIntervalMs(String time) {
        String[] tokens = time.toLowerCase(Locale.getDefault()).split("\\s+");
        int totalMinutes = 0;
        for (int i = 0; i < tokens.length - 1; i++) {
            try {
                int value = Integer.parseInt(tokens[i]);
                String unit = tokens[i + 1];
                if (unit.startsWith("hour")) {
                    totalMinutes += value * 60;
                } else if (unit.startsWith("minute")) {
                    totalMinutes += value;
                }
            } catch (NumberFormatException ignored) {}
        }
        return totalMinutes > 0 ? totalMinutes * 60L * 1000L : 0L;
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.quick_notif_widget);

        Intent intent = new Intent(context, QuickNotifWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, intent);
        views.setEmptyView(R.id.widget_list, R.id.widget_empty);

        // Pending intent template for list item clicks
        Intent clickIntent = new Intent(context, QuickNotifWidgetProvider.class);
        PendingIntent clickPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent);

        Intent appIntent = new Intent(context, MainActivity.class);
        PendingIntent appPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent);

        Intent refreshIntent = new Intent(context, QuickNotifWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                (int) (System.currentTimeMillis() & 0xFFFFFFF),
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent);

        Intent addIntent = new Intent(context, QuickNotifWidgetProvider.class);
        addIntent.setAction(ACTION_ADD);
        addIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent addPendingIntent = PendingIntent.getBroadcast(
                context,
                (int) (System.currentTimeMillis() & 0xFFFFFFF) + 1,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_add, addPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private void schedulePeriodicUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, QuickNotifWidgetProvider.class);
        intent.setAction(ACTION_ALARM_TICK);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_PERIODIC_UPDATE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long triggerAt = SystemClock.elapsedRealtime() + WIDGET_REFRESH_INTERVAL_MS;
        if (alarmManager != null) {
            alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, WIDGET_REFRESH_INTERVAL_MS, pendingIntent);
        }
    }

    private void cancelPeriodicUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, QuickNotifWidgetProvider.class);
        intent.setAction(ACTION_ALARM_TICK);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_PERIODIC_UPDATE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
