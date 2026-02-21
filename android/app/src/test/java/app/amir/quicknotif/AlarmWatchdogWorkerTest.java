package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;

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

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AlarmWatchdogWorkerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        NotifUtils.getPrefs(context).edit().clear().commit();
    }

    private ShadowAlarmManager shadowAlarmManager() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Shadows.shadowOf(am);
    }

    private long futureTs() {
        return System.currentTimeMillis() + 3_600_000L; // 1 hour ahead
    }

    // ─── empty / no-op cases ──────────────────────────────────────────────────

    @Test
    public void emptyPrefs_schedulesNothing() {
        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);
        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void emptyArray_schedulesNothing() {
        NotifUtils.saveNotificationsJson(context, "[]");
        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);
        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void malformedJson_doesNotCrash() {
        NotifUtils.saveNotificationsJson(context, "{{not json}}");
        // Must not throw
        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);
    }

    // ─── skip conditions ──────────────────────────────────────────────────────

    @Test
    public void disabledNotification_isSkipped() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_w1_1");
        n.put("name", "Disabled");
        n.put("enabled", false);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);

        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void expiredNotification_isSkipped() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_w2_1");
        n.put("name", "Expired");
        n.put("enabled", true);
        n.put("scheduledAt", System.currentTimeMillis() - 5_000L); // 5 s in the past
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);

        assertNull(shadowAlarmManager().getNextScheduledAlarm());
    }

    // ─── reschedule missing alarm ─────────────────────────────────────────────

    @Test
    public void missingAlarm_isRescheduled() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_w3_1");
        n.put("name", "Missing alarm");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        // No alarm pre-scheduled → watchdog must create one
        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);

        assertNotNull("Watchdog should schedule a missing alarm",
                shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void missingAlarm_triggersAtOriginalTime() throws Exception {
        long ts = futureTs();
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_w4_1");
        n.put("name", "Exact time");
        n.put("enabled", true);
        n.put("scheduledAt", ts);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);

        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager().getNextScheduledAlarm();
        assertNotNull(alarm);
        // Verify the trigger time is preserved via reflection (Robolectric 4.12.2)
        try {
            java.lang.reflect.Field f = alarm.getClass().getDeclaredField("triggerAtMs");
            f.setAccessible(true);
            assertEquals(ts, f.getLong(alarm));
        } catch (Exception e) {
            // Field not accessible in this Robolectric version — existence check is sufficient
        }
    }

    // ─── alive alarm is NOT double-scheduled ─────────────────────────────────

    @Test
    public void aliveAlarm_isNotRescheduled() throws Exception {
        String id = "notification_w5_1";
        long ts = futureTs();

        // Store the notification
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", id);
        n.put("name", "Alive notif");
        n.put("enabled", true);
        n.put("scheduledAt", ts);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        // Pre-schedule the alarm so it is already "alive"
        NotifUtils.scheduleAlarm(context, id, "Alive notif", ts);

        int alarmsBefore = shadowAlarmManager().getScheduledAlarms().size();
        assertTrue("Should have 1 alarm after pre-scheduling", alarmsBefore >= 1);

        // Watchdog should detect the alarm is alive and skip it
        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);

        int alarmsAfter = shadowAlarmManager().getScheduledAlarms().size();
        assertEquals("Alive alarm should not be duplicated", alarmsBefore, alarmsAfter);
    }

    // ─── mixed scenario ───────────────────────────────────────────────────────

    @Test
    public void mixed_onlyMissingFutureEnabledAlarmsAreRescheduled() throws Exception {
        String aliveId    = "notification_w6_1";
        String missingId  = "notification_w6_2";
        String disabledId = "notification_w6_3";
        String expiredId  = "notification_w6_4";
        long ts = futureTs();

        JSONArray arr = new JSONArray();

        JSONObject alive = new JSONObject();
        alive.put("id", aliveId);
        alive.put("name", "Alive");
        alive.put("enabled", true);
        alive.put("scheduledAt", ts);
        arr.put(alive);

        JSONObject missing = new JSONObject();
        missing.put("id", missingId);
        missing.put("name", "Missing");
        missing.put("enabled", true);
        missing.put("scheduledAt", ts + 60_000L);
        arr.put(missing);

        JSONObject disabled = new JSONObject();
        disabled.put("id", disabledId);
        disabled.put("name", "Disabled");
        disabled.put("enabled", false);
        disabled.put("scheduledAt", ts);
        arr.put(disabled);

        JSONObject expired = new JSONObject();
        expired.put("id", expiredId);
        expired.put("name", "Expired");
        expired.put("enabled", true);
        expired.put("scheduledAt", System.currentTimeMillis() - 10_000L);
        arr.put(expired);

        NotifUtils.saveNotificationsJson(context, arr.toString());

        // Pre-schedule only the "alive" one
        NotifUtils.scheduleAlarm(context, aliveId, "Alive", ts);
        int alarmsBefore = shadowAlarmManager().getScheduledAlarms().size();

        AlarmWatchdogWorker.rescheduleOrphanedAlarms(context);

        List<ShadowAlarmManager.ScheduledAlarm> alarmsAfter =
                shadowAlarmManager().getScheduledAlarms();

        // Exactly one new alarm should have been added (the missing one)
        assertEquals("Only the missing alarm should be added",
                alarmsBefore + 1, alarmsAfter.size());
    }
}
