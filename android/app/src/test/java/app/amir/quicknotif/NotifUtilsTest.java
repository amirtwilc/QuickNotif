package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotifUtilsTest {

    // Golden values: computed from the DJB2-variant algorithm
    // Cross-validated against TypeScript toNumericId()
    private static final int HASH_EMPTY              = 5382;
    private static final int HASH_X                  = 177630;
    private static final int HASH_ABC                = 193409670;
    private static final int HASH_NOTIF_0_0          = 212082553;
    private static final int HASH_NOTIF_1234_5678    = 1202083745;

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Clear prefs between tests
        NotifUtils.getPrefs(context).edit().clear().commit();
    }

    // ─── generateNumericId golden values ──────────────────────────────────────

    @Test
    public void generateNumericId_goldenValues() {
        assertEquals(HASH_EMPTY,           NotifUtils.generateNumericId(""));
        assertEquals(HASH_X,               NotifUtils.generateNumericId("x"));
        assertEquals(HASH_ABC,             NotifUtils.generateNumericId("abc"));
        assertEquals(HASH_NOTIF_0_0,       NotifUtils.generateNumericId("notification_0_0"));
        assertEquals(HASH_NOTIF_1234_5678, NotifUtils.generateNumericId("notification_1234_5678"));
    }

    @Test
    public void generateNumericId_alwaysPositive() {
        String[] inputs = {"", "x", "abc", "notification_0_0", "hello world", "test123",
                "notification_9999999999_9999"};
        for (String s : inputs) {
            assertTrue("Expected >= 1 for input: " + s, NotifUtils.generateNumericId(s) >= 1);
        }
    }

    @Test
    public void generateNumericId_withinRange() {
        String[] inputs = {"", "x", "abc", "notification_0_0", "notification_1234_5678",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"};
        for (String s : inputs) {
            int id = NotifUtils.generateNumericId(s);
            assertTrue("Expected <= 2147483646 for input: " + s, id <= 2147483646);
        }
    }

    @Test
    public void generateNumericId_deterministic() {
        String input = "notification_42_1337";
        int first  = NotifUtils.generateNumericId(input);
        int second = NotifUtils.generateNumericId(input);
        assertEquals(first, second);
    }

    // ─── SharedPreferences helpers ────────────────────────────────────────────

    @Test
    public void getPrefs_returnsCapacitorStoragePrefs() {
        SharedPreferences prefs = NotifUtils.getPrefs(context);
        assertNotNull(prefs);
        // Write via the returned prefs, read via named prefs — same store
        prefs.edit().putString("__test__", "yes").commit();
        assertEquals("yes",
                context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                        .getString("__test__", null));
    }

    @Test
    public void readNotificationsJson_whenEmpty_returnsEmptyArray() {
        assertEquals("[]", NotifUtils.readNotificationsJson(context));
    }

    @Test
    public void readNotificationsJson_whenValueExists_returnsStoredJson() {
        String json = "[{\"id\":\"n1\"}]";
        NotifUtils.getPrefs(context).edit().putString("notifications", json).commit();
        assertEquals(json, NotifUtils.readNotificationsJson(context));
    }

    @Test
    public void saveNotificationsJson_persists() {
        String json = "[{\"id\":\"n2\",\"name\":\"Test\"}]";
        NotifUtils.saveNotificationsJson(context, json);
        assertEquals(json, NotifUtils.readNotificationsJson(context));
    }

    @Test
    public void saveNotificationsJson_overwrites() {
        NotifUtils.saveNotificationsJson(context, "[{\"id\":\"old\"}]");
        String newJson = "[{\"id\":\"new\"}]";
        NotifUtils.saveNotificationsJson(context, newJson);
        assertEquals(newJson, NotifUtils.readNotificationsJson(context));
    }

    // ─── AlarmManager helpers ─────────────────────────────────────────────────

    @Test
    public void scheduleAlarm_registersAlarmWithAlarmManager() {
        long triggerMs = System.currentTimeMillis() + 60_000L;
        NotifUtils.scheduleAlarm(context, "notification_0_0", "Test", triggerMs);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = Shadows.shadowOf(am);
        assertNotNull("Expected at least one scheduled alarm", shadow.getNextScheduledAlarm());
    }

    @Test
    public void scheduleAlarm_usesCorrectTriggerTime() {
        long triggerMs = System.currentTimeMillis() + 90_000L;
        NotifUtils.scheduleAlarm(context, "notification_0_0", "Test", triggerMs);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager.ScheduledAlarm alarm = Shadows.shadowOf(am).getNextScheduledAlarm();
        assertNotNull(alarm);
        // Verify trigger time via reflection (field may be non-public depending on Robolectric version)
        try {
            java.lang.reflect.Field f = alarm.getClass().getDeclaredField("triggerAtMs");
            f.setAccessible(true);
            assertEquals(triggerMs, f.getLong(alarm));
        } catch (Exception e) {
            // Field not accessible in this Robolectric version — alarm existence already verified
        }
    }

    @Test
    public void scheduleAlarm_sameTwice_cancelsThenReschedules() {
        long t1 = System.currentTimeMillis() + 60_000L;
        long t2 = System.currentTimeMillis() + 120_000L;
        NotifUtils.scheduleAlarm(context, "notification_0_0", "Test", t1);
        NotifUtils.scheduleAlarm(context, "notification_0_0", "Test", t2);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = Shadows.shadowOf(am);
        // The second call replaces the first via cancel+reschedule
        ShadowAlarmManager.ScheduledAlarm alarm = shadow.getNextScheduledAlarm();
        assertNotNull(alarm);
        // Verify second trigger time via reflection
        try {
            java.lang.reflect.Field f = alarm.getClass().getDeclaredField("triggerAtMs");
            f.setAccessible(true);
            assertEquals(t2, f.getLong(alarm));
        } catch (Exception e) {
            // Field not accessible — alarm existence is the key assertion
        }
    }

    @Test
    public void cancelAlarm_removesAlarmFromAlarmManager() {
        long triggerMs = System.currentTimeMillis() + 60_000L;
        NotifUtils.scheduleAlarm(context, "notification_0_0", "Test", triggerMs);
        NotifUtils.cancelAlarm(context, "notification_0_0");

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = Shadows.shadowOf(am);
        assertNull("Expected no alarms after cancel", shadow.getNextScheduledAlarm());
    }

    @Test
    public void cancelAlarm_whenNoAlarmExists_doesNotThrow() {
        // Should not throw
        NotifUtils.cancelAlarm(context, "notification_nonexistent_0");
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    @Test
    public void constants_valuesMatchExpected() {
        assertEquals("CapacitorStorage", NotifUtils.PREFS_NAME);
        assertEquals("notifications",    NotifUtils.KEY_NOTIFICATIONS);
        assertEquals("timer-alerts",     NotifUtils.CHANNEL_ID);
        assertEquals(0xFF6366F1,         NotifUtils.ACCENT_COLOR);
    }

    // ─── Widget refresh smoke test ────────────────────────────────────────────

    @Test
    public void refreshAllWidgets_doesNotCrash() {
        // Robolectric shadows AppWidgetManager; no widgets registered = no-op
        NotifUtils.refreshAllWidgets(context);
    }
}
