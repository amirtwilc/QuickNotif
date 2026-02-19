package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RadioButton;
import android.widget.TextView;

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
public class RescheduleActivityTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        NotifUtils.getPrefs(context).edit().clear().commit();
    }

    private ActivityController<RescheduleActivity> buildActivity(
            String id, String name, String type) {
        Intent intent = new Intent(context, RescheduleActivity.class);
        if (id   != null) intent.putExtra("notificationId",   id);
        if (name != null) intent.putExtra("notificationName", name);
        if (type != null) intent.putExtra("notificationType", type);
        return Robolectric.buildActivity(RescheduleActivity.class, intent).create().start().resume();
    }

    private ShadowAlarmManager shadowAlarmManager() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Shadows.shadowOf(am);
    }

    private void storeNotification(String id, String name, String type, boolean enabled,
                                   long scheduledAt) throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", id);
        n.put("name", name);
        n.put("type", type);
        n.put("enabled", enabled);
        n.put("scheduledAt", scheduledAt);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
    }

    // ─── onCreate ─────────────────────────────────────────────────────────────

    @Test
    public void onCreate_nullNotificationId_finishesActivity() {
        Intent intent = new Intent(context, RescheduleActivity.class);
        // No notificationId extra
        ActivityController<RescheduleActivity> ctrl =
                Robolectric.buildActivity(RescheduleActivity.class, intent)
                        .create().start().resume();
        assertTrue("Activity should finish when notificationId is null",
                ctrl.get().isFinishing());
    }

    @Test
    public void onCreate_setsDialogTitleWithNotificationName() {
        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_1_1", "My Reminder", "relative");
        RescheduleActivity act = ctrl.get();

        TextView title = act.findViewById(R.id.dialog_title);
        assertTrue("Title should contain notification name",
                title.getText().toString().contains("My Reminder"));
    }

    @Test
    public void onCreate_absoluteType_checksAbsoluteRadio() {
        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_2_2", "Alarm", "absolute");
        RescheduleActivity act = ctrl.get();

        RadioButton absBtn = act.findViewById(R.id.type_absolute);
        assertTrue("Absolute radio should be checked for absolute type", absBtn.isChecked());
    }

    @Test
    public void onCreate_relativeType_checksRelativeRadio() {
        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_3_3", "Timer", "relative");
        RescheduleActivity act = ctrl.get();

        RadioButton relBtn = act.findViewById(R.id.type_relative);
        assertTrue("Relative radio should be checked for relative type", relBtn.isChecked());
    }

    // ─── handleConfirm — relative ─────────────────────────────────────────────

    @Test
    public void handleConfirm_updatesNotificationTimeInStorage() throws Exception {
        storeNotification("notification_10_1", "Test", "relative", true,
                System.currentTimeMillis() + 60_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_10_1", "Test", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("2");
        act.minutesInput.setText("0");
        act.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject n = new JSONArray(json).getJSONObject(0);
        assertEquals("2 hours", n.getString("time"));
        assertEquals("relative", n.getString("type"));
    }

    @Test
    public void handleConfirm_relativeType_derivesInterval() throws Exception {
        storeNotification("notification_11_1", "Timer", "relative", true,
                System.currentTimeMillis() + 60_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_11_1", "Timer", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("1");
        act.minutesInput.setText("30");
        act.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject n = new JSONArray(json).getJSONObject(0);
        assertEquals(5_400_000L, n.getLong("interval"));
    }

    @Test
    public void handleConfirm_absoluteType_removesIntervalField() throws Exception {
        // Notification was previously relative with interval
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_12_1");
        n.put("name", "Switch type");
        n.put("type", "relative");
        n.put("time", "1 hour");
        n.put("interval", 3_600_000L);
        n.put("enabled", true);
        n.put("scheduledAt", System.currentTimeMillis() + 60_000L);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_12_1", "Switch type", "relative");
        RescheduleActivity act = ctrl.get();
        // Switch to absolute
        act.typeGroup.check(R.id.type_absolute);

        // Set future hour via reflection
        Field hourField = BaseNotificationActivity.class.getDeclaredField("selectedHour");
        hourField.setAccessible(true);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int futureHour = (cal.get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24;
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 23) {
            hourField.set(act, futureHour);
        } else {
            hourField.set(act, 12); // safe fallback
        }

        act.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject updated = new JSONArray(json).getJSONObject(0);
        assertEquals("absolute", updated.getString("type"));
        assertFalse("interval field should be removed", updated.has("interval"));
    }

    @Test
    public void updateNotification_parsesHoursFromTimeString() throws Exception {
        storeNotification("notification_13_1", "Hours parse", "relative", true,
                System.currentTimeMillis() + 60_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_13_1", "Hours parse", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("2");
        act.minutesInput.setText("0");
        act.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject updated = new JSONArray(json).getJSONObject(0);
        assertEquals(7_200_000L, updated.getLong("interval")); // 2h = 7200s = 7200000ms
    }

    @Test
    public void updateNotification_setsEnabledToTrue() throws Exception {
        storeNotification("notification_14_1", "Was disabled", "relative", false,
                System.currentTimeMillis() - 5_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_14_1", "Was disabled", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("0");
        act.minutesInput.setText("10");
        act.handleConfirm();

        String json = NotifUtils.readNotificationsJson(context);
        JSONObject updated = new JSONArray(json).getJSONObject(0);
        assertTrue("enabled should be set to true after reschedule", updated.getBoolean("enabled"));
    }

    @Test
    public void handleConfirm_zeroRelativeDuration_doesNotSave() throws Exception {
        storeNotification("notification_15_1", "Zero dur", "relative", true,
                System.currentTimeMillis() + 60_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_15_1", "Zero dur", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("");
        act.minutesInput.setText("");
        act.handleConfirm();

        // Activity should not finish (not saved)
        assertFalse("Activity should not finish on zero duration", act.isFinishing());
    }

    @Test
    public void handleConfirm_validInput_schedulesAlarm() throws Exception {
        storeNotification("notification_16_1", "Alarm test", "relative", true,
                System.currentTimeMillis() + 60_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_16_1", "Alarm test", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("0");
        act.minutesInput.setText("15");
        act.handleConfirm();

        assertNotNull("Expected alarm scheduled after reschedule",
                shadowAlarmManager().getNextScheduledAlarm());
    }

    @Test
    public void handleConfirm_success_finishesActivity() throws Exception {
        storeNotification("notification_17_1", "Finish test", "relative", true,
                System.currentTimeMillis() + 60_000L);

        ActivityController<RescheduleActivity> ctrl =
                buildActivity("notification_17_1", "Finish test", "relative");
        RescheduleActivity act = ctrl.get();
        act.typeGroup.check(R.id.type_relative);
        act.hoursInput.setText("0");
        act.minutesInput.setText("20");
        act.handleConfirm();

        assertTrue("Activity should finish after successful reschedule", act.isFinishing());
    }
}
