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
import org.robolectric.shadows.ShadowApplication;

import java.lang.reflect.Method;
import java.util.Calendar;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuickNotifWidgetProviderTest {

    private Context context;
    private QuickNotifWidgetProvider provider;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        provider = new QuickNotifWidgetProvider();
        NotifUtils.getPrefs(context).edit().clear().commit();
    }

    private ShadowAlarmManager shadowAlarmManager() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Shadows.shadowOf(am);
    }

    private long futureTs() {
        return System.currentTimeMillis() + 3_600_000L;
    }

    private void storeNotifications(JSONArray arr) {
        NotifUtils.saveNotificationsJson(context, arr.toString());
    }

    // ─── Delete action ────────────────────────────────────────────────────────

    @Test
    public void deleteAction_removesTargetNotification() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_1_1");
        n1.put("name", "Keep");
        arr.put(n1);
        JSONObject n2 = new JSONObject();
        n2.put("id", "notification_2_2");
        n2.put("name", "Delete me");
        arr.put(n2);
        storeNotifications(arr);

        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_DELETE);
        intent.putExtra("notificationId", "notification_2_2");
        provider.onReceive(context, intent);

        String json = NotifUtils.readNotificationsJson(context);
        assertFalse("Deleted notification should not be in storage", json.contains("notification_2_2"));
        assertTrue("Other notification should still be in storage", json.contains("notification_1_1"));
    }

    @Test
    public void deleteAction_preservesOtherNotifications() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_1_1");
        n1.put("name", "Keep me");
        n1.put("type", "relative");
        arr.put(n1);
        JSONObject n2 = new JSONObject();
        n2.put("id", "notification_2_2");
        n2.put("name", "Delete me");
        arr.put(n2);
        storeNotifications(arr);

        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_DELETE);
        intent.putExtra("notificationId", "notification_2_2");
        provider.onReceive(context, intent);

        String json = NotifUtils.readNotificationsJson(context);
        JSONArray result = new JSONArray(json);
        assertEquals(1, result.length());
        assertEquals("Keep me", result.getJSONObject(0).getString("name"));
        assertEquals("relative", result.getJSONObject(0).getString("type"));
    }

    @Test
    public void deleteAction_idNotFound_doesNotModifyStorage() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_1_1");
        arr.put(n1);
        storeNotifications(arr);

        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_DELETE);
        intent.putExtra("notificationId", "notification_nonexistent_99");
        provider.onReceive(context, intent);

        String json = NotifUtils.readNotificationsJson(context);
        assertTrue(json.contains("notification_1_1"));
    }

    @Test
    public void deleteAction_emptyStorage_doesNotCrash() {
        NotifUtils.saveNotificationsJson(context, "[]");
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_DELETE);
        intent.putExtra("notificationId", "notification_1_1");
        // Should not throw
        provider.onReceive(context, intent);
    }

    @Test
    public void deleteAction_nullId_doesNothing() {
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_DELETE);
        // No notificationId extra
        provider.onReceive(context, intent);
        // Storage unchanged (still "[]")
        assertEquals("[]", NotifUtils.readNotificationsJson(context));
    }

    // ─── Reactivate action ────────────────────────────────────────────────────

    @Test
    public void reactivateAction_relativeType_withInterval_updatesScheduledAt() throws Exception {
        long interval = 3_600_000L; // 1 hour
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_50_1");
        n.put("name", "Relative with interval");
        n.put("type", "relative");
        n.put("time", "1 hour");
        n.put("interval", interval);
        n.put("enabled", false);
        n.put("scheduledAt", System.currentTimeMillis() - 1000L);
        arr.put(n);
        storeNotifications(arr);

        long before = System.currentTimeMillis();
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
        intent.putExtra("notificationId", "notification_50_1");
        provider.onReceive(context, intent);
        long after = System.currentTimeMillis();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject updated = new JSONArray(json).getJSONObject(0);
        long newScheduledAt = updated.getLong("scheduledAt");
        long expectedMin = before + interval;
        long expectedMax = after + interval;
        assertTrue("scheduledAt should be approx now + interval",
                newScheduledAt >= expectedMin && newScheduledAt <= expectedMax + 100);
        assertTrue("enabled should be true", updated.getBoolean("enabled"));
    }

    @Test
    public void reactivateAction_relativeType_noInterval_parsesTimeString() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_51_1");
        n.put("name", "Relative no interval");
        n.put("type", "relative");
        n.put("time", "1 hour 30 minutes");
        // No interval field
        n.put("enabled", false);
        n.put("scheduledAt", System.currentTimeMillis() - 1000L);
        arr.put(n);
        storeNotifications(arr);

        long before = System.currentTimeMillis();
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
        intent.putExtra("notificationId", "notification_51_1");
        provider.onReceive(context, intent);
        long after = System.currentTimeMillis();

        long expectedInterval = 5_400_000L; // 1h30m
        String json = NotifUtils.readNotificationsJson(context);
        JSONObject updated = new JSONArray(json).getJSONObject(0);
        long newScheduledAt = updated.getLong("scheduledAt");
        assertTrue("scheduledAt should be approx now + 5400000",
                newScheduledAt >= before + expectedInterval &&
                newScheduledAt <= after + expectedInterval + 200);
    }

    @Test
    public void reactivateAction_absoluteType_futureTime_schedulesToday() throws Exception {
        Calendar now = Calendar.getInstance();
        // Use a time one hour ahead to ensure it's future
        int futureHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24;
        String time = String.format("%02d:%02d", futureHour, 0);

        // Only run this test if the future time is later today (i.e., not midnight wrap)
        if (now.get(Calendar.HOUR_OF_DAY) < 23) {
            JSONArray arr = new JSONArray();
            JSONObject n = new JSONObject();
            n.put("id", "notification_52_1");
            n.put("name", "Absolute future");
            n.put("type", "absolute");
            n.put("time", time);
            n.put("enabled", false);
            n.put("scheduledAt", System.currentTimeMillis() - 1000L);
            arr.put(n);
            storeNotifications(arr);

            Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
            intent.putExtra("notificationId", "notification_52_1");
            provider.onReceive(context, intent);

            String json = NotifUtils.readNotificationsJson(context);
            JSONObject updated = new JSONArray(json).getJSONObject(0);
            long newScheduledAt = updated.getLong("scheduledAt");
            assertTrue("newScheduledAt should be in the future", newScheduledAt > System.currentTimeMillis());
        }
    }

    @Test
    public void reactivateAction_invalidType_skipsReactivation() throws Exception {
        long originalScheduledAt = System.currentTimeMillis() - 1000L;
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_53_1");
        n.put("name", "Invalid type");
        n.put("type", ""); // invalid — must be rejected, not inferred
        n.put("time", "09:45");
        n.put("enabled", false);
        n.put("scheduledAt", originalScheduledAt);
        arr.put(n);
        storeNotifications(arr);

        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
        intent.putExtra("notificationId", "notification_53_1");
        provider.onReceive(context, intent);

        // Storage must be unchanged — invalid type is rejected, not inferred
        String json = NotifUtils.readNotificationsJson(context);
        JSONObject stored = new JSONArray(json).getJSONObject(0);
        assertEquals("", stored.getString("type"));
        assertEquals(originalScheduledAt, stored.getLong("scheduledAt"));
    }

    @Test
    public void reactivateAction_missingType_skipsReactivation() throws Exception {
        long originalScheduledAt = System.currentTimeMillis() - 1000L;
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_54_1");
        n.put("name", "Missing type");
        // type field absent
        n.put("time", "2 hours");
        n.put("enabled", false);
        n.put("scheduledAt", originalScheduledAt);
        arr.put(n);
        storeNotifications(arr);

        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
        intent.putExtra("notificationId", "notification_54_1");
        provider.onReceive(context, intent);

        // Storage must be unchanged — missing type is rejected, not inferred
        String json = NotifUtils.readNotificationsJson(context);
        JSONObject stored = new JSONArray(json).getJSONObject(0);
        assertEquals("", stored.optString("type", ""));
        assertEquals(originalScheduledAt, stored.getLong("scheduledAt"));
    }

    @Test
    public void reactivateAction_schedulesAlarmInAlarmManager() throws Exception {
        long interval = 1_800_000L; // 30 min
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_55_1");
        n.put("name", "Alarm check");
        n.put("type", "relative");
        n.put("time", "30 minutes");
        n.put("interval", interval);
        n.put("enabled", false);
        n.put("scheduledAt", System.currentTimeMillis() - 1000L);
        arr.put(n);
        storeNotifications(arr);

        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
        intent.putExtra("notificationId", "notification_55_1");
        provider.onReceive(context, intent);

        assertNotNull("Expected alarm in AlarmManager after reactivate",
                shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void reactivateAction_idNotFound_doesNotCrash() {
        NotifUtils.saveNotificationsJson(context, "[]");
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_REACTIVATE);
        intent.putExtra("notificationId", "notification_nonexistent");
        // Should not throw
        provider.onReceive(context, intent);
    }

    // ─── calculateNewScheduleTime via reflection ──────────────────────────────

    private long callCalculateNewScheduleTime(String type, String time, long interval) throws Exception {
        Method m = QuickNotifWidgetProvider.class.getDeclaredMethod(
                "calculateNewScheduleTime", String.class, String.class, long.class);
        m.setAccessible(true);
        return (long) m.invoke(provider, type, time, interval);
    }

    @Test
    public void calculateNewScheduleTime_relative_withInterval() throws Exception {
        long interval = 3_600_000L;
        long before = System.currentTimeMillis();
        long result = callCalculateNewScheduleTime("relative", "", interval);
        long after = System.currentTimeMillis();

        assertTrue(result >= before + interval);
        assertTrue(result <= after + interval + 50);
    }

    @Test
    public void calculateNewScheduleTime_relative_fromTimeString() throws Exception {
        long before = System.currentTimeMillis();
        long result = callCalculateNewScheduleTime("relative", "1 hour 30 minutes", 0L);
        long after = System.currentTimeMillis();

        long expectedInterval = 5_400_000L;
        assertTrue(result >= before + expectedInterval);
        assertTrue(result <= after + expectedInterval + 100);
    }

    @Test
    public void calculateNewScheduleTime_absolute_futureTime() throws Exception {
        // Use next hour, guaranteed to be in the future unless it's 23:xx
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) < 23) {
            int futureHour = now.get(Calendar.HOUR_OF_DAY) + 1;
            String time = String.format("%02d:00", futureHour);
            long result = callCalculateNewScheduleTime("absolute", time, 0L);
            assertTrue("Absolute future time should be > now", result > System.currentTimeMillis());
        }
    }

    @Test
    public void calculateNewScheduleTime_absolute_pastTime() throws Exception {
        // Use 00:00 which is always in the past (except exactly at midnight)
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) > 0) {
            long result = callCalculateNewScheduleTime("absolute", "00:00", 0L);
            assertTrue("Past time should schedule for tomorrow", result > System.currentTimeMillis());
        }
    }

    @Test
    public void calculateNewScheduleTime_absolute_invalidFormat_fallsBack() throws Exception {
        long result = callCalculateNewScheduleTime("absolute", "not-a-time", 0L);
        // Invalid format cannot be parsed - method contract returns -1 to signal failure
        assertEquals(-1L, result);
    }

    // ─── Routing actions ──────────────────────────────────────────────────────

    @Test
    public void rescheduleAction_startsRescheduleActivity() {
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_RESCHEDULE);
        intent.putExtra("notificationId", "notification_60_1");
        intent.putExtra("notificationName", "Test");
        intent.putExtra("notificationType", "relative");
        provider.onReceive(context, intent);

        Intent started = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull("Expected RescheduleActivity to be started", started);
        assertEquals(RescheduleActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void addAction_startsAddNotificationActivity() {
        Intent intent = new Intent(QuickNotifWidgetProvider.ACTION_ADD);
        provider.onReceive(context, intent);

        Intent started = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull("Expected AddNotificationActivity to be started", started);
        assertEquals(AddNotificationActivity.class.getName(), started.getComponent().getClassName());
    }
}
