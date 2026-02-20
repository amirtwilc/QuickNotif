package app.amir.quicknotif;

import android.util.Log;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class RescheduleActivity extends BaseNotificationActivity {
    private static final String TAG = "RescheduleActivity";

    private String notificationId;
    private String notificationName;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_reschedule;
    }

    @Override
    protected void onAfterViewsInitialized() {
        notificationId   = getIntent().getStringExtra("notificationId");
        notificationName = getIntent().getStringExtra("notificationName");
        String notificationType = getIntent().getStringExtra("notificationType");

        if (notificationId == null) {
            finish();
            return;
        }

        TextView titleText = findViewById(R.id.dialog_title);
        titleText.setText("Reschedule: " + (notificationName != null && !notificationName.isEmpty()
                ? notificationName : "Notification"));

        if (NotifUtils.TYPE_ABSOLUTE.equals(notificationType)) {
            ((RadioButton) findViewById(R.id.type_absolute)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.type_relative)).setChecked(true);
        }
    }

    @Override
    protected void handleConfirm() {
        try {
            String type;
            String time;
            long scheduledAt;

            int checkedId = typeGroup.getCheckedRadioButtonId();

            if (checkedId == R.id.type_absolute) {
                type = NotifUtils.TYPE_ABSOLUTE;
                time = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                scheduledAt = calculateAbsoluteTime(selectedHour, selectedMinute);
            } else {
                type = NotifUtils.TYPE_RELATIVE;

                String hoursStr   = hoursInput.getText().toString().trim();
                String minutesStr = minutesInput.getText().toString().trim();

                int hours   = hoursStr.isEmpty()   ? 0 : Integer.parseInt(hoursStr);
                int minutes = minutesStr.isEmpty() ? 0 : Integer.parseInt(minutesStr);

                if (hours == 0 && minutes == 0) {
                    Toast.makeText(this, "Please enter a duration", Toast.LENGTH_SHORT).show();
                    return;
                }

                time = buildTimeString(hours, minutes);
                scheduledAt = System.currentTimeMillis() + calculateRelativeMs(hours, minutes);
            }

            updateNotification(notificationId, time, type, scheduledAt);
            NotifUtils.scheduleAlarm(this, notificationId, notificationName, scheduledAt);
            NotifUtils.writeToLog("RESCHEDULE", notificationId, notificationName, scheduledAt);
            NotifUtils.refreshAllWidgets(this);

            Toast.makeText(this, "Notification rescheduled", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Log.e(TAG, "Error in handleConfirm", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNotification(String id, String time, String type, long scheduledAt) {
        try {
            String notificationsJson = NotifUtils.readNotificationsJson(this);
            JSONArray array = new JSONArray(notificationsJson);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (id.equals(obj.optString(NotifUtils.JSON_KEY_ID, ""))) {
                    obj.put(NotifUtils.JSON_KEY_TIME, time);
                    obj.put(NotifUtils.JSON_KEY_TYPE, type);
                    obj.put(NotifUtils.JSON_KEY_SCHEDULED_AT, scheduledAt);
                    obj.put(NotifUtils.JSON_KEY_UPDATED_AT, System.currentTimeMillis());
                    obj.put(NotifUtils.JSON_KEY_ENABLED, true);

                    if (NotifUtils.TYPE_RELATIVE.equals(type)) {
                        // Parse the time string to derive the interval in ms
                        String[] parts = time.toLowerCase().split(" ");
                        long totalMinutes = 0;
                        for (int j = 0; j < parts.length - 1; j += 2) {
                            try {
                                int value = Integer.parseInt(parts[j]);
                                String unit = parts[j + 1];
                                if (unit.contains("hour")) {
                                    totalMinutes += value * 60L;
                                } else if (unit.contains("minute")) {
                                    totalMinutes += value;
                                }
                            } catch (Exception ignored) {}
                        }
                        obj.put(NotifUtils.JSON_KEY_INTERVAL, totalMinutes * 60 * 1000);
                    } else {
                        obj.remove(NotifUtils.JSON_KEY_INTERVAL);
                    }

                    break;
                }
            }

            NotifUtils.saveNotificationsJson(this, array.toString());
            Log.d(TAG, "✅ Updated SharedPreferences");

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update notification in storage", e);
        }
    }
}
