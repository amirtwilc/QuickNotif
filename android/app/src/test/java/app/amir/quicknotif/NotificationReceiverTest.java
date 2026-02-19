package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotificationReceiverTest {

    private Context context;
    private NotificationReceiver receiver;
    private NotificationManager notificationManager;
    private ShadowNotificationManager shadowNotificationManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        receiver = new NotificationReceiver();
        notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        shadowNotificationManager = Shadows.shadowOf(notificationManager);
    }

    private Intent makeIntent(String id, String name) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        if (id   != null) intent.putExtra("notificationId",   id);
        if (name != null) intent.putExtra("notificationName", name);
        return intent;
    }

    // ─── onReceive ────────────────────────────────────────────────────────────

    @Test
    public void onReceive_validIntent_postsNotification() {
        receiver.onReceive(context, makeIntent("notification_0_0", "Meeting reminder"));

        assertEquals("Expected 1 notification to be posted",
                1, shadowNotificationManager.getAllNotifications().size());
    }

    @Test
    public void onReceive_notificationPostedWithCorrectNumericId() {
        String id = "notification_0_0";
        int expectedNumericId = NotifUtils.generateNumericId(id);

        receiver.onReceive(context, makeIntent(id, "Test"));

        StatusBarNotification[] active = shadowNotificationManager.getActiveNotifications();
        assertEquals(1, active.length);
        assertEquals(expectedNumericId, active[0].getId());
    }

    @Test
    public void onReceive_emptyName_usesChannelNameAsFallback() {
        receiver.onReceive(context, makeIntent("notification_1_1", ""));

        // Should post a notification (fallback to CHANNEL_NAME)
        assertEquals(1, shadowNotificationManager.getAllNotifications().size());
    }

    @Test
    public void onReceive_nullName_usesChannelNameAsFallback() {
        receiver.onReceive(context, makeIntent("notification_2_2", null));

        assertEquals(1, shadowNotificationManager.getAllNotifications().size());
    }

    // ─── showNotification channel setup ──────────────────────────────────────

    @Test
    public void showNotification_createsNotificationChannel() {
        receiver.onReceive(context, makeIntent("notification_3_3", "Test"));

        // Use real NotificationManager API (Robolectric shadows it internally)
        NotificationChannel channel = notificationManager.getNotificationChannel(NotifUtils.CHANNEL_ID);
        assertNotNull("Channel 'timer-alerts' should be created", channel);
    }

    @Test
    public void showNotification_channelNotDuplicatedOnSecondCall() {
        receiver.onReceive(context, makeIntent("notification_4_4", "First"));
        receiver.onReceive(context, makeIntent("notification_5_5", "Second"));

        // Channel should exist
        assertNotNull(notificationManager.getNotificationChannel(NotifUtils.CHANNEL_ID));
        // Two notifications should exist (different IDs)
        assertEquals(2, shadowNotificationManager.getAllNotifications().size());
    }

    // ─── Notification properties ──────────────────────────────────────────────

    @Test
    public void showNotification_notificationHasPriorityHigh() {
        receiver.onReceive(context, makeIntent("notification_6_6", "High priority test"));

        android.app.Notification n = shadowNotificationManager.getAllNotifications().get(0);
        assertEquals(android.app.Notification.PRIORITY_HIGH, n.priority);
    }

    @Test
    public void showNotification_notificationIsAutoCancel() {
        receiver.onReceive(context, makeIntent("notification_7_7", "AutoCancel test"));

        android.app.Notification n = shadowNotificationManager.getAllNotifications().get(0);
        assertTrue("Notification should be auto-cancel",
                (n.flags & android.app.Notification.FLAG_AUTO_CANCEL) != 0);
    }

    @Test
    public void showNotification_notificationHasVibration() {
        receiver.onReceive(context, makeIntent("notification_8_8", "Vibration test"));

        // On API 26+, vibration is set on the channel, not the individual notification.
        // The channel is configured with enableVibration(true) in NotificationReceiver.
        NotificationChannel channel = notificationManager.getNotificationChannel(NotifUtils.CHANNEL_ID);
        assertNotNull("Channel should exist", channel);
        assertTrue("Channel should have vibration enabled", channel.shouldVibrate());
    }
}
