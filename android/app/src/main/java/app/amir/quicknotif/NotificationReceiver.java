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
 * Receives alarm broadcasts and shows notifications
 * Used by widget reactivate/reschedule actions
 */
public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String notificationId = intent.getStringExtra("notificationId");
        String notificationName = intent.getStringExtra("notificationName");

        Log.d(TAG, "🔔 Received notification broadcast: " + notificationName);

        if (notificationName == null || notificationName.isEmpty()) {
            notificationName = "Quick Notif";
        }

        // Show the notification
        showNotification(context, notificationId, notificationName);

        // Write to log
        writeToLog(context, "FIRE", notificationId, notificationName);
    }

    private void showNotification(Context context, String id, String name) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            Log.e(TAG, "❌ NotificationManager is null");
            return;
        }

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "timer-alerts",
                    "Quick Notif",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Timer and notification alerts");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(0xFF6366F1); // Purple
            notificationManager.createNotificationChannel(channel);
        }

        // Intent to open app when notification is tapped
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "timer-alerts")
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(0xFF6366F1)
                .setContentTitle("Quick Notif")
                .setContentText(name)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(name))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(0xFF6366F1, 1000, 1000)
                .setContentIntent(pendingIntent);

        // Show notification
        int numericId = generateNumericId(id);
        notificationManager.notify(numericId, builder.build());

        Log.d(TAG, "✅ Notification shown: " + name);
    }

    private int generateNumericId(String stringId) {
        int hash = 5381;
        for (int i = 0; i < stringId.length(); i++) {
            hash = ((hash << 5) + hash) ^ stringId.charAt(i);
        }
        return Math.abs(hash) % 2147483646 + 1;
    }

    private void writeToLog(Context context, String type, String id, String name) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            String timestamp = sdf.format(new java.util.Date());

            String logLine = String.format("[%s] [%s] [ID:%s...] [%s] 🔔 Notification fired\n",
                    timestamp,
                    type,
                    id != null ? id.substring(0, Math.min(12, id.length())) : "unknown",
                    name
            );

            // Write to log file
            java.io.File documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS);
            java.io.File logFile = new java.io.File(documentsDir, "notification_debug.log");

            java.io.FileWriter writer = new java.io.FileWriter(logFile, true);
            writer.write(logLine);
            writer.close();

            Log.d(TAG, "✅ Wrote to log file");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to write to log", e);
        }
    }
}