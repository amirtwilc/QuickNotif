package app.amir.quicknotif;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RemoteViewsService that provides list data for the QuickNotif home screen widget.
 *
 * <p>Android widgets run inside the home screen's process. A {@link android.widget.ListView}
 * inside a widget cannot be populated directly — it requires a {@link RemoteViewsService}
 * to supply data across process boundaries. This class fulfills that role.
 *
 * <p>Workflow:
 * <ol>
 *   <li>{@link QuickNotifWidgetProvider#updateAppWidget} calls {@code setRemoteAdapter(...)}
 *       with an {@link Intent} targeting this service.</li>
 *   <li>The system binds to this service and calls {@link #onGetViewFactory}, which returns
 *       a {@link QuickNotifRemoteViewsFactory}.</li>
 *   <li>The factory's {@code onCreate} loads notifications from SharedPreferences, sorts them
 *       (expired first, then by scheduled time), and holds them in memory.</li>
 *   <li>The system calls {@code getViewAt} for each visible row, which inflates
 *       {@code widget_item} and sets text, colors, and click intents per row.</li>
 *   <li>On widget refresh ({@code notifyAppWidgetViewDataChanged}), the system calls
 *       {@code onDataSetChanged}, which reloads from SharedPreferences to reflect any
 *       changes made by widget actions or the React app.</li>
 * </ol>
 */
public class QuickNotifWidgetService extends RemoteViewsService {

    private static final int COLOR_EXPIRED          = 0xFFFCA5A5; // rose-300  — expired text
    private static final int COLOR_ACTIVE_PRIMARY   = 0xFFFFFFFF; // white     — active name/date
    private static final int COLOR_ACTIVE_SECONDARY = 0xFFF0F0F0; // light grey — active time

    private static final String TIME_DISPLAY_FORMAT = "HH:mm";
    private static final String DATE_DISPLAY_FORMAT = "MMM d";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new QuickNotifRemoteViewsFactory(this.getApplicationContext());
    }

    /**
     * RemoteViewsFactory that acts as the adapter for the widget's ListView.
     * Static to avoid holding an implicit reference to the outer service instance.
     */
    private static class QuickNotifRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private final Context context;
        private final List<NotificationData> notifications;

        QuickNotifRemoteViewsFactory(Context context) {
            this.context = context;
            this.notifications = new ArrayList<>();
        }

        /** Called once when the factory is first created. Performs initial data load. */
        @Override
        public void onCreate() {
            loadNotifications();
        }

        /**
         * Called when {@code notifyAppWidgetViewDataChanged} is triggered. Reloads
         * from SharedPreferences so the widget reflects the latest state.
         */
        @Override
        public void onDataSetChanged() {
            loadNotifications();
        }

        /** Called when the factory is no longer needed. Clears the in-memory list. */
        @Override
        public void onDestroy() {
            notifications.clear();
        }

        /** Returns the number of rows to render. */
        @Override
        public int getCount() {
            return notifications.size();
        }

        /**
         * Returns a fully configured {@link RemoteViews} for the given row.
         * Sets text, colors (expired vs active), and fill-in intents for action buttons.
         */
        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item);
            if (position < notifications.size()) {
                NotificationData notification = notifications.get(position);

                rv.setTextViewText(R.id.notification_name, notification.name().isEmpty() ? "Unnamed" : notification.name());
                rv.setTextViewText(R.id.notification_time, notification.timeString());
                rv.setTextViewText(R.id.notification_date, notification.dateString());

                if (notification.isExpired()) {
                    rv.setTextColor(R.id.notification_name, COLOR_EXPIRED);
                    rv.setTextColor(R.id.notification_time, COLOR_EXPIRED);
                    rv.setTextColor(R.id.notification_date, COLOR_EXPIRED);
                    rv.setViewVisibility(R.id.expired_actions, View.VISIBLE);

                    String reactivateText = notification.time().isEmpty()
                            ? "Reactivate"
                            : "Reactivate (" + notification.time() + ")";
                    rv.setTextViewText(R.id.btn_reactivate, reactivateText);

                    Intent rescheduleIntent = new Intent();
                    rescheduleIntent.setAction(QuickNotifWidgetProvider.ACTION_RESCHEDULE);
                    rescheduleIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_ID, notification.id());
                    rescheduleIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_NAME, notification.name());
                    rescheduleIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_TYPE, notification.type());
                    rv.setOnClickFillInIntent(R.id.btn_reschedule, rescheduleIntent);

                    Intent deleteIntent = new Intent();
                    deleteIntent.setAction(QuickNotifWidgetProvider.ACTION_DELETE);
                    deleteIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_ID, notification.id());
                    rv.setOnClickFillInIntent(R.id.btn_delete, deleteIntent);

                    Intent reactivateIntent = new Intent();
                    reactivateIntent.setAction(QuickNotifWidgetProvider.ACTION_REACTIVATE);
                    reactivateIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_ID, notification.id());
                    rv.setOnClickFillInIntent(R.id.btn_reactivate, reactivateIntent);
                } else {
                    rv.setTextColor(R.id.notification_name, COLOR_ACTIVE_PRIMARY);
                    rv.setTextColor(R.id.notification_time, COLOR_ACTIVE_SECONDARY);
                    rv.setTextColor(R.id.notification_date, COLOR_ACTIVE_PRIMARY);
                    rv.setViewVisibility(R.id.expired_actions, View.GONE);
                }
            }
            return rv;
        }

        /**
         * Returns a loading placeholder shown while {@code getViewAt} is being prepared.
         * {@code null} uses the system default loading view.
         */
        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        /**
         * Returns the number of distinct row layouts this adapter produces.
         * {@code 1} means all rows share the same layout, enabling proper view recycling.
         */
        @Override
        public int getViewTypeCount() {
            return 1;
        }

        /**
         * Returns an ID for the item at the given position. Used alongside
         * {@link #hasStableIds()} to allow the framework to animate list changes.
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Returns {@code true} to allow the framework to animate list changes rather
         * than doing a full redraw. Note: the IDs returned by {@link #getItemId} are
         * position-based and will shift on reorder — acceptable for this use case.
         */
        @Override
        public boolean hasStableIds() {
            return true;
        }

        private static final String TAG = "QuickNotifWidget";

        private void loadNotifications() {
            notifications.clear();
            try {
                String notificationsJson = NotifUtils.readNotificationsJson(context);
                JSONArray array = new JSONArray(notificationsJson);

                SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_DISPLAY_FORMAT, Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_DISPLAY_FORMAT, Locale.getDefault());

                List<NotificationData> tempNotifications = new ArrayList<>();
                long currentTime = System.currentTimeMillis();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    boolean enabled = obj.optBoolean(NotifUtils.JSON_KEY_ENABLED, false);
                    String id = obj.optString(NotifUtils.JSON_KEY_ID, "");

                    long scheduledAt = NotifUtils.parseScheduledAt(obj);

                    if (enabled && scheduledAt > 0) {
                        String name = obj.optString(NotifUtils.JSON_KEY_NAME, "");
                        String time = obj.optString(NotifUtils.JSON_KEY_TIME, "");
                        String type = obj.optString(NotifUtils.JSON_KEY_TYPE, NotifUtils.TYPE_RELATIVE);
                        String timeString = timeFormat.format(new Date(scheduledAt));
                        String dateString = dateFormat.format(new Date(scheduledAt));
                        boolean isExpired = scheduledAt <= currentTime;
                        tempNotifications.add(new NotificationData(id, name, timeString, dateString, scheduledAt, isExpired, time, type));
                    }
                }

                // Sort: expired first, then by scheduled time ascending
                Collections.sort(tempNotifications, (n1, n2) -> {
                    if (n1.isExpired() != n2.isExpired()) {
                        return n1.isExpired() ? -1 : 1;
                    }
                    return Long.compare(n1.scheduledAt(), n2.scheduledAt());
                });

                notifications.addAll(tempNotifications);
            } catch (Exception e) {
                AppLogger.e(TAG, "❌ Failed to load notifications from storage — possible JSON corruption", e);
            }
        }

        /** Immutable data holder for a single notification row in the widget list. */
        private record NotificationData(
                String id,
                String name,
                String timeString,
                String dateString,
                long scheduledAt,
                boolean isExpired,
                String time,
                String type
        ) {}
    }
}
