package app.amir.quicknotif;

import android.util.Log;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class AddNotificationActivity extends BaseNotificationActivity {
    private static final String TAG = "AddNotificationActivity";

    private EditText nameInput;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_add_notification;
    }

    @Override
    protected void onAfterViewsInitialized() {
        nameInput = findViewById(R.id.name_input);
        ((RadioButton) findViewById(R.id.type_relative)).setChecked(true);
    }

    private String generateNotificationId() {
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 10000);
        return String.format(Locale.US, "notification_%d_%d", timestamp, random);
    }

    @Override
    protected void handleConfirm() {
        try {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a notification name", Toast.LENGTH_SHORT).show();
                return;
            }

            String type;
            String time;
            long scheduledAt;
            long interval = 0;

            int checkedId = typeGroup.getCheckedRadioButtonId();

            if (checkedId == R.id.type_absolute) {
                type = "absolute";
                time = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                scheduledAt = calculateAbsoluteTime(selectedHour, selectedMinute);
            } else {
                type = "relative";

                String hoursStr   = hoursInput.getText().toString().trim();
                String minutesStr = minutesInput.getText().toString().trim();

                int hours   = hoursStr.isEmpty()   ? 0 : Integer.parseInt(hoursStr);
                int minutes = minutesStr.isEmpty() ? 0 : Integer.parseInt(minutesStr);

                if (hours == 0 && minutes == 0) {
                    Toast.makeText(this, "Please enter a duration", Toast.LENGTH_SHORT).show();
                    return;
                }

                time      = buildTimeString(hours, minutes);
                interval  = calculateRelativeMs(hours, minutes);
                scheduledAt = System.currentTimeMillis() + interval;
            }

            String notificationId = generateNotificationId();
            createNotification(notificationId, name, time, type, scheduledAt, interval);
            NotifUtils.scheduleAlarm(this, notificationId, name, scheduledAt);
            NotifUtils.writeToLog(this, "CREATE", notificationId, name, scheduledAt);
            NotifUtils.refreshAllWidgets(this);

            Toast.makeText(this, "Notification created", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Log.e(TAG, "Error in handleConfirm", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotification(String id, String name, String time, String type,
                                    long scheduledAt, long interval) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(this);
            JSONArray array = new JSONArray(notificationsJson);

            JSONObject newNotification = new JSONObject();
            newNotification.put("id", id);
            newNotification.put("name", name);
            newNotification.put("time", time);
            newNotification.put("type", type);
            newNotification.put("enabled", true);
            newNotification.put("scheduledAt", scheduledAt);
            newNotification.put("updatedAt", System.currentTimeMillis());

            if ("relative".equals(type) && interval > 0) {
                newNotification.put("interval", interval);
            }

            array.put(newNotification);
            NotifUtils.saveNotificationsJson(this, array.toString());

            Log.d(TAG, "✅ Created notification in SharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create notification in storage", e);
        }
    }
}
