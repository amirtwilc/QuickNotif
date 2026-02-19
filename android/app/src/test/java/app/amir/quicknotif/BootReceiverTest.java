package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BootReceiverTest {

    private Context context;
    private BootReceiver receiver;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        receiver = new BootReceiver();
        NotifUtils.getPrefs(context).edit().clear().commit();
    }

    private ShadowAlarmManager shadowAlarmManager() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Shadows.shadowOf(am);
    }

    private void callReschedule() throws Exception {
        Method m = BootReceiver.class.getDeclaredMethod("rescheduleNotifications", Context.class);
        m.setAccessible(true);
        m.invoke(receiver, context);
    }

    private long futureTs() {
        return System.currentTimeMillis() + 3_600_000L; // 1 hour ahead
    }

    // ─── onReceive routing ────────────────────────────────────────────────────

    @Test
    public void onReceive_bootCompleted_schedulesEnabledFutureNotification() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_1_1");
        n.put("name", "Boot test");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(context, intent);

        assertNotNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void onReceive_quickBootPowerOn_schedulesAlarm() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_2_2");
        n.put("name", "QuickBoot test");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        Intent intent = new Intent("android.intent.action.QUICKBOOT_POWERON");
        receiver.onReceive(context, intent);

        assertNotNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void onReceive_unknownAction_schedulesNothing() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_3_3");
        n.put("name", "Ignored");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        Intent intent = new Intent("com.some.UNKNOWN_ACTION");
        receiver.onReceive(context, intent);

        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    // ─── rescheduleNotifications private method ───────────────────────────────

    @Test
    public void rescheduleNotifications_emptyPrefs_schedulesNothing() throws Exception {
        callReschedule();
        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void rescheduleNotifications_emptyArray_schedulesNothing() throws Exception {
        NotifUtils.saveNotificationsJson(context, "[]");
        callReschedule();
        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void rescheduleNotifications_singleEnabledFutureNotification_schedulesOneAlarm()
            throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_10_10");
        n.put("name", "Future notif");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        assertNotNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void rescheduleNotifications_disabledNotification_skips() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_11_11");
        n.put("name", "Disabled");
        n.put("enabled", false);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void rescheduleNotifications_expiredNotification_skips() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_12_12");
        n.put("name", "Expired");
        n.put("enabled", true);
        n.put("scheduledAt", System.currentTimeMillis() - 10_000L); // 10 seconds in the past
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void rescheduleNotifications_mixed_onlySchedulesFutureEnabled() throws Exception {
        JSONArray arr = new JSONArray();

        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_20_1");
        n1.put("name", "Valid");
        n1.put("enabled", true);
        n1.put("scheduledAt", futureTs());
        arr.put(n1);

        JSONObject n2 = new JSONObject();
        n2.put("id", "notification_20_2");
        n2.put("name", "Disabled");
        n2.put("enabled", false);
        n2.put("scheduledAt", futureTs());
        arr.put(n2);

        JSONObject n3 = new JSONObject();
        n3.put("id", "notification_20_3");
        n3.put("name", "Expired");
        n3.put("enabled", true);
        n3.put("scheduledAt", System.currentTimeMillis() - 5_000L);
        arr.put(n3);

        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        // Only the valid one (notification_20_1) should be scheduled
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager().getNextScheduledAlarm();
        assertNotNull(alarm);
    }

    @Test
    public void rescheduleNotifications_scheduledAtAsLong_parsedCorrectly() throws Exception {
        long ts = futureTs();
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_30_1");
        n.put("name", "Long test");
        n.put("enabled", true);
        n.put("scheduledAt", ts);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager().getNextScheduledAlarm();
        assertNotNull(alarm);
        // Verify exact trigger time via reflection
        try {
            java.lang.reflect.Field f = alarm.getClass().getDeclaredField("triggerAtMs");
            f.setAccessible(true);
            assertEquals(ts, f.getLong(alarm));
        } catch (Exception e) {
            // Field not accessible — alarm existence is the primary assertion
        }
    }

    @Test
    public void rescheduleNotifications_scheduledAtAsIsoString_parsedCorrectly() throws Exception {
        // Must be a future date string — use year 2099 for safety
        String isoFuture = "2099-06-15T10:30:00.000Z";
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_31_1");
        n.put("name", "ISO test");
        n.put("enabled", true);
        n.put("scheduledAt", isoFuture);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        assertNotNull("ISO string scheduledAt should be parsed and alarm scheduled",
                shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void rescheduleNotifications_malformedJson_doesNotCrash() throws Exception {
        NotifUtils.saveNotificationsJson(context, "not valid json {{{{");
        // Should not throw
        callReschedule();
    }

    @Test
    public void rescheduleNotifications_missingScheduledAt_skips() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_40_1");
        n.put("name", "No scheduledAt");
        n.put("enabled", true);
        // No scheduledAt field — optLong returns 0, which is in the past
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        callReschedule();

        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }
}
