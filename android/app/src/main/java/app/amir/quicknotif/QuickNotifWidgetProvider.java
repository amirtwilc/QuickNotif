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

public class QuickNotifWidgetProvider extends AppWidgetProvider {

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
        super.onReceive(context, intent);
        String action = intent.getAction();

        if (ACTION_DELETE.equals(action)) {
            String notificationId = intent.getStringExtra("notificationId");
            if (notificationId != null) {
                deleteNotification(context, notificationId);
                NotifUtils.refreshAllWidgets(context);
            }
        } else if (ACTION_REACTIVATE.equals(action)) {
            String notificationId = intent.getStringExtra("notificationId");
            if (notificationId != null) {
                reactivateNotification(context, notificationId);
                NotifUtils.refreshAllWidgets(context);
            }
        } else if (ACTION_RESCHEDULE.equals(action)) {
            String notificationId = intent.getStringExtra("notificationId");
            String notificationName = intent.getStringExtra("notificationName");
            String notificationType = intent.getStringExtra("notificationType");
            if (notificationId != null) {
                Intent rescheduleIntent = new Intent(context, RescheduleActivity.class);
                rescheduleIntent.putExtra("notificationId", notificationId);
                rescheduleIntent.putExtra("notificationName", notificationName);
                rescheduleIntent.putExtra("notificationType", notificationType);
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
                if (!notificationId.equals(obj.optString("id", ""))) {
                    newArray.put(obj);
                }
            }

            NotifUtils.saveNotificationsJson(context, newArray.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reactivateNotification(Context context, String notificationId) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(context);
            JSONArray array = new JSONArray(notificationsJson);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.optString("id", "");

                if (id.equals(notificationId)) {
                    String name = obj.optString("name", "");
                    String time = obj.optString("time", "");
                    String type = obj.optString("type", "");

                    // Heuristic: infer type if missing or invalid
                    if (!"relative".equals(type) && !"absolute".equals(type)) {
                        String lt = time == null ? "" : time.toLowerCase(Locale.getDefault());
                        if (lt.contains("hour") || lt.contains("minute")) {
                            type = "relative";
                        } else if (lt.contains(":")) {
                            type = "absolute";
                        } else {
                            type = "relative";
                        }
                        obj.put("type", type);
                    }

                    long interval = 0L;
                    if ("relative".equals(type)) {
                        interval = obj.optLong("interval", 0L);
                        if (interval <= 0 && time != null && !time.isEmpty()) {
                            String[] tokens = time.toLowerCase(Locale.getDefault()).split("\\s+");
                            int totalMinutes = 0;
                            for (int t = 0; t < tokens.length - 1; t++) {
                                try {
                                    int value = Integer.parseInt(tokens[t]);
                                    String unit = tokens[t + 1];
                                    if (unit.startsWith("hour")) {
                                        totalMinutes += value * 60;
                                    } else if (unit.startsWith("minute")) {
                                        totalMinutes += value;
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                            if (totalMinutes > 0) {
                                interval = totalMinutes * 60L * 1000L;
                                obj.put("interval", interval);
                            }
                        }
                    }

                    long newScheduledAt = calculateNewScheduleTime(type, time, interval);

                    obj.put("scheduledAt", newScheduledAt);
                    obj.put("updatedAt", System.currentTimeMillis());
                    obj.put("enabled", true);

                    NotifUtils.saveNotificationsJson(context, array.toString());
                    NotifUtils.scheduleAlarm(context, id, name, newScheduledAt);
                    NotifUtils.writeToLog(context, "REACTIVATE", id, name, newScheduledAt);

                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long calculateNewScheduleTime(String type, String time, long interval) {
        try {
            if ("relative".equals(type)) {
                if (interval > 0) {
                    return System.currentTimeMillis() + interval;
                }
                if (time != null && !time.isEmpty()) {
                    // Parse natural language like "1 hour 30 minutes"
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
                    if (totalMinutes > 0) {
                        return System.currentTimeMillis() + (totalMinutes * 60L * 1000L);
                    }
                }
            } else {
                // For absolute time, schedule for next occurrence using Calendar
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
            e.printStackTrace();
        }

        // Fallback: schedule for 1 hour from now
        return System.currentTimeMillis() + (60 * 60 * 1000L);
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
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long interval = 5 * 60 * 1000L;
        long triggerAt = SystemClock.elapsedRealtime() + interval;
        if (alarmManager != null) {
            alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, interval, pendingIntent);
        }
    }

    private void cancelPeriodicUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, QuickNotifWidgetProvider.class);
        intent.setAction(ACTION_ALARM_TICK);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
