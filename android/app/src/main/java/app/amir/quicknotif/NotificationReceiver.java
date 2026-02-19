package app.amir.quicknotif;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Receives alarm broadcasts and shows notifications.
 * Used by widget reactivate/reschedule actions and BootReceiver.
 */
public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String notificationId = intent.getStringExtra("notificationId");
        String notificationName = intent.getStringExtra("notificationName");

        Log.d(TAG, "🔔 Received notification broadcast: " + notificationName);

        if (notificationName == null || notificationName.isEmpty()) {
            notificationName = NotifUtils.CHANNEL_NAME;
        }

        showNotification(context, notificationId, notificationName);
        NotifUtils.writeToLog(context, "FIRE", notificationId, notificationName, 0L);
    }

    private void showNotification(Context context, String id, String name) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            Log.e(TAG, "❌ NotificationManager is null");
            return;
        }

        // Create notification channel for Android O+ only if it doesn't already exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && notificationManager.getNotificationChannel(NotifUtils.CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    NotifUtils.CHANNEL_ID,
                    NotifUtils.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Timer and notification alerts");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(NotifUtils.ACCENT_COLOR);
            notificationManager.createNotificationChannel(channel);
        }

        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotifUtils.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(NotifUtils.ACCENT_COLOR)
                .setContentTitle(NotifUtils.CHANNEL_NAME)
                .setContentText(name)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(name))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(NotifUtils.ACCENT_COLOR, 1000, 1000)
                .setContentIntent(pendingIntent);

        int numericId = NotifUtils.generateNumericId(id);
        notificationManager.notify(numericId, builder.build());

        Log.d(TAG, "✅ Notification shown: " + name);
    }
}
