package app.amir.quicknotif;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class QuickNotifWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new QuickNotifRemoteViewsFactory(this.getApplicationContext());
    }

    private static class QuickNotifRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private final Context context;
        private final List<NotificationData> notifications;

        QuickNotifRemoteViewsFactory(Context context) {
            this.context = context;
            this.notifications = new ArrayList<>();
        }

        @Override
        public void onCreate() {
            loadNotifications();
        }

        @Override
        public void onDataSetChanged() {
            loadNotifications();
        }

        @Override
        public void onDestroy() {
            notifications.clear();
        }

        @Override
        public int getCount() {
            return notifications.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item);
            if (position < notifications.size()) {
                NotificationData notification = notifications.get(position);

                // Set text content
                rv.setTextViewText(R.id.notification_name, notification.name.isEmpty() ? "Unnamed" : notification.name);
                rv.setTextViewText(R.id.notification_time, notification.timeString);
                rv.setTextViewText(R.id.notification_date, notification.dateString);

                // Set colors based on expired status
                if (notification.isExpired) {
                    // CHANGED: Softer rose color instead of harsh red
                    rv.setTextColor(R.id.notification_name, 0xFFFCA5A5); // Soft rose (was 0xFFFF5252)
                    rv.setTextColor(R.id.notification_time, 0xFFFCA5A5);
                    rv.setTextColor(R.id.notification_date, 0xFFFCA5A5);
                    rv.setViewVisibility(R.id.expired_actions, View.VISIBLE);

                    // Format reactivate button text with time/duration
                    String reactivateText = "Reactivate";
                    if (!notification.time.isEmpty()) {
                        if ("absolute".equals(notification.type)) {
                            reactivateText = "Reactivate (" + notification.time + ")";
                        } else {
                            reactivateText = "Reactivate (" + notification.time + ")";
                        }
                    }
                    rv.setTextViewText(R.id.btn_reactivate, reactivateText);

                    // Set up delete button with fill-in intent
                    Intent deleteIntent = new Intent();
                    deleteIntent.setAction(QuickNotifWidgetProvider.ACTION_DELETE);
                    deleteIntent.putExtra("notificationId", notification.id);
                    rv.setOnClickFillInIntent(R.id.btn_delete, deleteIntent);

                    // Set up reactivate button with fill-in intent
                    Intent reactivateIntent = new Intent();
                    reactivateIntent.setAction(QuickNotifWidgetProvider.ACTION_REACTIVATE);
                    reactivateIntent.putExtra("notificationId", notification.id);
                    rv.setOnClickFillInIntent(R.id.btn_reactivate, reactivateIntent);
                } else {
                    rv.setTextColor(R.id.notification_name, 0xFFFFFFFF); // White
                    rv.setTextColor(R.id.notification_time, 0xFFF0F0F0);
                    rv.setTextColor(R.id.notification_date, 0xFFFFFFFF);
                    rv.setViewVisibility(R.id.expired_actions, View.GONE);
                }
            }
            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        private void loadNotifications() {
            notifications.clear();
            try {
                SharedPreferences prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
                String notificationsJson = prefs.getString("notifications", "[]");
                JSONArray array = new JSONArray(notificationsJson);
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                List<NotificationData> tempNotifications = new ArrayList<>();
                long currentTime = System.currentTimeMillis();

                for (int i = 0; i < array.length(); i++) {
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
                            } catch (Exception ignored2) { }
                        }
                    }

                    // Include both active (future) and expired notifications
                    if (enabled && scheduledAt > 0) {
                        String name = obj.optString("name", "");
                        String time = obj.optString("time", "");
                        String type = obj.optString("type", "relative");
                        String timeString = timeFormat.format(new Date(scheduledAt));
                        String dateString = dateFormat.format(new Date(scheduledAt));
                        boolean isExpired = scheduledAt <= currentTime;
                        tempNotifications.add(new NotificationData(id, name, timeString, dateString, scheduledAt, isExpired, time, type));
                    }
                }

                // Sort by scheduled time (earliest first, expired at top)
                Collections.sort(tempNotifications, new Comparator<NotificationData>() {
                    @Override
                    public int compare(NotificationData n1, NotificationData n2) {
                        // Expired items first
                        if (n1.isExpired != n2.isExpired) {
                            return n1.isExpired ? -1 : 1;
                        }
                        // Then by time
                        return Long.compare(n1.scheduledAt, n2.scheduledAt);
                    }
                });

                notifications.addAll(tempNotifications);
            } catch (Exception ignored) { }
        }

        private static class NotificationData {
            final String id;
            final String name;
            final String timeString;
            final String dateString;
            final long scheduledAt;
            final boolean isExpired;
            final String time;
            final String type;

            NotificationData(String id, String name, String timeString, String dateString, long scheduledAt, boolean isExpired, String time, String type) {
                this.id = id;
                this.name = name;
                this.timeString = timeString;
                this.dateString = dateString;
                this.scheduledAt = scheduledAt;
                this.isExpired = isExpired;
                this.time = time;
                this.type = type;
            }
        }
    }
}