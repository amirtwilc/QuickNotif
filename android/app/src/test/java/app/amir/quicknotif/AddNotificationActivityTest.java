package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.RadioButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AddNotificationActivityTest {

    private Context context;
    private ActivityController<AddNotificationActivity> controller;
    private AddNotificationActivity activity;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        NotifUtils.getPrefs(context).edit().clear().commit();
        controller = Robolectric.buildActivity(AddNotificationActivity.class).create().start().resume();
        activity = controller.get();
    }

    private ShadowAlarmManager shadowAlarmManager() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Shadows.shadowOf(am);
    }

    private void setName(String name) {
        EditText nameInput = activity.findViewById(R.id.name_input);
        nameInput.setText(name);
    }

    private void selectRelative(int hours, int minutes) {
        ((RadioButton) activity.findViewById(R.id.type_relative)).setChecked(true);
        activity.typeGroup.check(R.id.type_relative);
        activity.hoursInput.setText(hours > 0 ? String.valueOf(hours) : "");
        activity.minutesInput.setText(minutes > 0 ? String.valueOf(minutes) : "");
    }

    private void selectAbsolute(int hour, int minute) throws Exception {
        ((RadioButton) activity.findViewById(R.id.type_absolute)).setChecked(true);
        activity.typeGroup.check(R.id.type_absolute);

        Field hourField   = BaseNotificationActivity.class.getDeclaredField("selectedHour");
        Field minuteField = BaseNotificationActivity.class.getDeclaredField("selectedMinute");
        hourField.setAccessible(true);
        minuteField.setAccessible(true);
        hourField.set(activity, hour);
        minuteField.set(activity, minute);
    }

    private String callGenerateNotificationId() throws Exception {
        Method m = AddNotificationActivity.class.getDeclaredMethod("generateNotificationId");
        m.setAccessible(true);
        return (String) m.invoke(activity);
    }

    // ─── Initial state ────────────────────────────────────────────────────────

    @Test
    public void onCreate_relativeRadioButton_isCheckedByDefault() {
        RadioButton rel = activity.findViewById(R.id.type_relative);
        assertTrue("Relative radio button should be checked by default", rel.isChecked());
    }

    // ─── generateNotificationId ───────────────────────────────────────────────

    @Test
    public void generateNotificationId_matchesExpectedFormat() throws Exception {
        String id = callGenerateNotificationId();
        assertTrue("ID should match pattern notification_<ts>_<rand>",
                id.matches("notification_\\d+_\\d+"));
    }

    @Test
    public void generateNotificationId_twoCallsAreUnique() throws Exception {
        String id1 = callGenerateNotificationId();
        Thread.sleep(2); // ensure different timestamp
        String id2 = callGenerateNotificationId();
        assertNotEquals("Two generated IDs should be unique", id1, id2);
    }

    // ─── handleConfirm validation ─────────────────────────────────────────────

    @Test
    public void handleConfirm_emptyName_doesNotCreateNotification() {
        setName("");
        selectRelative(0, 30);
        activity.handleConfirm();

        assertEquals("[]", NotifUtils.readNotificationsJson(context));
    }

    @Test
    public void handleConfirm_zeroRelativeDuration_doesNotCreateNotification() {
        setName("Test notification");
        selectRelative(0, 0);
        activity.handleConfirm();

        assertEquals("[]", NotifUtils.readNotificationsJson(context));
    }

    // ─── Successful creation ──────────────────────────────────────────────────

    @Test
    public void handleConfirm_validRelativeInput_createsNotificationInStorage() throws Exception {
        setName("Remind me");
        selectRelative(1, 30);

        long before = System.currentTimeMillis();
        activity.handleConfirm();
        long after = System.currentTimeMillis();

        String json = NotifUtils.readNotificationsJson(context);
        JSONArray arr = new JSONArray(json);
        assertEquals(1, arr.length());

        JSONObject n = arr.getJSONObject(0);
        assertEquals("Remind me",  n.getString("name"));
        assertEquals("relative",   n.getString("type"));
        assertEquals("1 hour 30 minutes", n.getString("time"));
        assertEquals(5_400_000L,   n.getLong("interval"));
        assertTrue(n.getBoolean("enabled"));
        assertTrue(n.getLong("scheduledAt") >= before + 5_400_000L);
        assertTrue(n.getLong("scheduledAt") <= after  + 5_400_000L + 100);
    }

    @Test
    public void handleConfirm_validAbsoluteInput_createsNotificationInStorage() throws Exception {
        setName("Alarm");
        // Use a future time: set selectedHour/Minute directly
        java.util.Calendar now = java.util.Calendar.getInstance();
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        int min  = now.get(java.util.Calendar.MINUTE);
        // Add 1 hour to ensure future
        int futureHour = (hour + 1) % 24;
        selectAbsolute(futureHour, min);

        activity.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONArray arr = new JSONArray(json);
        assertEquals(1, arr.length());

        JSONObject n = arr.getJSONObject(0);
        assertEquals("Alarm",    n.getString("name"));
        assertEquals("absolute", n.getString("type"));
        assertTrue(n.getLong("scheduledAt") > System.currentTimeMillis());
    }

    @Test
    public void handleConfirm_validInput_schedulesAlarm() throws Exception {
        setName("Schedule check");
        selectRelative(0, 15);
        activity.handleConfirm();

        assertNotNull("Expected alarm to be scheduled in AlarmManager",
                shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void handleConfirm_success_finishesActivity() {
        setName("Done");
        selectRelative(0, 5);
        activity.handleConfirm();

        assertTrue("Activity should finish after successful creation", activity.isFinishing());
    }

    @Test
    public void createNotification_appendsToExistingNotifications() throws Exception {
        // Pre-populate storage with one notification
        JSONArray existing = new JSONArray();
        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_existing_1");
        n1.put("name", "Existing");
        n1.put("enabled", true);
        n1.put("scheduledAt", System.currentTimeMillis() + 60_000L);
        existing.put(n1);
        NotifUtils.saveNotificationsJson(context, existing.toString());

        // Recreate activity so it reads fresh state
        controller.destroy();
        controller = Robolectric.buildActivity(AddNotificationActivity.class).create().start().resume();
        activity = controller.get();

        setName("New notification");
        selectRelative(0, 10);
        activity.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONArray result = new JSONArray(json);
        assertEquals("Should have 2 notifications", 2, result.length());
    }

    @Test
    public void handleConfirm_validRelativeInput_correctScheduledAt() throws Exception {
        setName("Timing test");
        selectRelative(0, 30); // 30 minutes = 1_800_000 ms

        long before = System.currentTimeMillis();
        activity.handleConfirm();
        long after = System.currentTimeMillis();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject n = new JSONArray(json).getJSONObject(0);
        long scheduledAt = n.getLong("scheduledAt");

        assertTrue(scheduledAt >= before + 1_800_000L);
        assertTrue(scheduledAt <= after  + 1_800_000L + 100);
    }
}
