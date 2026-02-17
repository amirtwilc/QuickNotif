package app.amir.quicknotif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class QuickNotifWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "app.amir.quicknotif.ACTION_REFRESH";
    public static final String ACTION_ALARM_TICK = "app.amir.quicknotif.ACTION_ALARM_TICK";
    public static final String ACTION_DELETE = "app.amir.quicknotif.ACTION_DELETE";
    public static final String ACTION_REACTIVATE = "app.amir.quicknotif.ACTION_REACTIVATE";
    public static final String ACTION_RESCHEDULE = "app.amir.quicknotif.ACTION_RESCHEDULE";
    public static final String ACTION_ADD = "app.amir.quicknotif.ACTION_ADD";

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
                refreshAllWidgets(context);
            }
        } else if (ACTION_REACTIVATE.equals(action)) {
            String notificationId = intent.getStringExtra("notificationId");
            if (notificationId != null) {
                reactivateNotification(context, notificationId);
                refreshAllWidgets(context);
            }

        } else if (ACTION_RESCHEDULE.equals(action)) {
            String notificationId = intent.getStringExtra("notificationId");
            String notificationName = intent.getStringExtra("notificationName");
            String notificationType = intent.getStringExtra("notificationType"); // ADD THIS LINE
            if (notificationId != null) {
                Intent rescheduleIntent = new Intent(context, RescheduleActivity.class);
                rescheduleIntent.putExtra("notificationId", notificationId);
                rescheduleIntent.putExtra("notificationName", notificationName);
                rescheduleIntent.putExtra("notificationType", notificationType); // ADD THIS LINE
                rescheduleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(rescheduleIntent);
            }
        } else if (ACTION_ADD.equals(action)) {
            Intent addIntent = new Intent(context, AddNotificationActivity.class);
            addIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(addIntent);
        } else if (ACTION_REFRESH.equals(action) || ACTION_ALARM_TICK.equals(action) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            refreshAllWidgets(context);
        }

    }

    private void refreshAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, QuickNotifWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void deleteNotification(Context context, String notificationId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            String notificationsJson = prefs.getString("notifications", "[]");
            JSONArray array = new JSONArray(notificationsJson);
            JSONArray newArray = new JSONArray();
            
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.optString("id", "");
                if (!id.equals(notificationId)) {
                    newArray.put(obj);
                }
            }
            
            prefs.edit().putString("notifications", newArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reactivateNotification(Context context, String notificationId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            String notificationsJson = prefs.getString("notifications", "[]");
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
                            // Default to relative if we can't tell
                            type = "relative";
                        }
                        obj.put("type", type);
                    }

                    long interval = 0L;
                    if ("relative".equals(type)) {
                        // Prefer stored interval, otherwise derive from the time string and persist it
                        interval = obj.optLong("interval", 0L);
                        if (interval <= 0 && time != null && !time.isEmpty()) {
                            // Parse patterns like "1 hour 30 minutes"
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
                                obj.put("interval", interval); // persist for future reactivations
                            }
                        }
                    }

                    long newScheduledAt = calculateNewScheduleTime(type, time, interval);

                    obj.put("scheduledAt", newScheduledAt);
                    obj.put("updatedAt", System.currentTimeMillis());
                    obj.put("enabled", true);

                    prefs.edit().putString("notifications", array.toString()).apply();
                    scheduleAndroidNotification(context, id, name, newScheduledAt);
                    writeToLog(context, "REACTIVATE", id, name, newScheduledAt);

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
                // For relative time, use the stored interval if available
                if (interval > 0) {
                    return System.currentTimeMillis() + interval;
                }
                if (time != null && !time.isEmpty()) {
                    // Try HH:mm format first (e.g., "01:30")
                    if (time.contains(":")) {
                        String[] parts = time.split(":");
                        int hours = Integer.parseInt(parts[0]);
                        int minutes = Integer.parseInt(parts[1]);
                        return System.currentTimeMillis() + (hours * 60 * 60 * 1000L) + (minutes * 60 * 1000L);
                    } else {
                        // Try natural language like "1 hour 30 minutes"
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
                }
            } else {
                // For absolute time, schedule for next occurrence
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date timeDate = timeFormat.parse(time);
                
                if (timeDate != null) {
                    long now = System.currentTimeMillis();
                    Date today = new Date(now);
                    today.setHours(timeDate.getHours());
                    today.setMinutes(timeDate.getMinutes());
                    today.setSeconds(0);
                    
                    long scheduledTime = today.getTime();
                    
                    // If time has passed today, schedule for tomorrow
                    if (scheduledTime <= now) {
                        scheduledTime += 24 * 60 * 60 * 1000L;
                    }
                    
                    return scheduledTime;
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

        // Set up pending intent template for list item clicks
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

        // Set up "+" button to open AddNotificationActivity
        Intent addIntent = new Intent(context, QuickNotifWidgetProvider.class);
        addIntent.setAction(ACTION_ADD);
        addIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent addPendingIntent = PendingIntent.getBroadcast(
                context,
                (int) (System.currentTimeMillis() & 0xFFFFFFF) + 1, // Different from refresh
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
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, interval, pendingIntent);
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

    /**
     * Actually schedule the notification in Android's AlarmManager
     * This is what was missing!
     */
    private void scheduleAndroidNotification(Context context, String notificationId, String name, long scheduledAt) {
        try {
            Log.d("QuickNotifWidget", "📅 Scheduling in Android: " + name + " at " + new Date(scheduledAt));

            // Create intent for NotificationReceiver
            Intent notificationIntent = new Intent(context, NotificationReceiver.class);
            notificationIntent.putExtra("notificationId", notificationId);
            notificationIntent.putExtra("notificationName", name);

            // Generate numeric ID
            int numericId = generateNumericId(notificationId);

            // Create pending intent
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    numericId,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Get AlarmManager
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            if (alarmManager != null) {
                // Cancel any existing alarm with the same ID to prevent duplicates
                alarmManager.cancel(pendingIntent);

                // Schedule the alarm
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

                Log.d("QuickNotifWidget", "✅ Notification scheduled in Android AlarmManager");
            } else {
                Log.e("QuickNotifWidget", "❌ AlarmManager is null");
            }
        } catch (Exception e) {
            Log.e("QuickNotifWidget", "❌ Failed to schedule Android notification", e);
        }
    }

    /**
     * Generate consistent numeric ID from string ID
     */
    private int generateNumericId(String stringId) {
        int hash = 5381;
        for (int i = 0; i < stringId.length(); i++) {
            hash = ((hash << 5) + hash) ^ stringId.charAt(i);
        }
        return Math.abs(hash) % 2147483646 + 1;
    }

    /**
     * Write widget actions to debug log file
     */
    private void writeToLog(Context context, String type, String id, String name, long scheduledAt) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            String timestamp = sdf.format(new Date());

            SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String scheduledTime = timeSdf.format(new Date(scheduledAt));

            String logLine = String.format("[%s] [%s] [ID:%s...] [%s] 🔄 Widget action for %s\n",
                    timestamp,
                    type,
                    id.substring(0, Math.min(12, id.length())),
                    name,
                    scheduledTime
            );

            // Write to same log file the app uses
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File logFile = new File(documentsDir, "notification_debug.log");

            FileWriter writer = new FileWriter(logFile, true); // append mode
            writer.write(logLine);
            writer.close();

            Log.d("QuickNotifWidget", "✅ Wrote to log file");
        } catch (Exception e) {
            Log.e("QuickNotifWidget", "❌ Failed to write to log", e);
        }
    }
}
