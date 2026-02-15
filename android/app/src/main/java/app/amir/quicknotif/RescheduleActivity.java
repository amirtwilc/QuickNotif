package app.amir.quicknotif;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RescheduleActivity extends Activity {
    private static final String TAG = "RescheduleActivity";

    private String notificationId;
    private String notificationName;
    private RadioGroup typeGroup;
    private LinearLayout absoluteContainer;
    private LinearLayout relativeContainer;
    private Button selectTimeButton;
    private EditText hoursInput;
    private EditText minutesInput;

    private int selectedHour = 12;
    private int selectedMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make it look like a dialog
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_reschedule);

        // Get notification ID and type from intent
        notificationId = getIntent().getStringExtra("notificationId");
        notificationName = getIntent().getStringExtra("notificationName");
        String notificationType = getIntent().getStringExtra("notificationType");

        if (notificationId == null) {
            finish();
            return;
        }

        initializeViews();
        setupListeners();

        // Set initial type based on notification's original type
        if ("absolute".equals(notificationType)) {
            ((RadioButton) findViewById(R.id.type_absolute)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.type_relative)).setChecked(true);
        }
    }

    private void initializeViews() {
        TextView titleText = findViewById(R.id.dialog_title);
        titleText.setText("Reschedule: " + (notificationName != null && !notificationName.isEmpty() ? notificationName : "Notification"));

        typeGroup = findViewById(R.id.type_group);
        absoluteContainer = findViewById(R.id.absolute_container);
        relativeContainer = findViewById(R.id.relative_container);
        selectTimeButton = findViewById(R.id.select_time_button);
        hoursInput = findViewById(R.id.hours_input);
        minutesInput = findViewById(R.id.minutes_input);

        Button cancelButton = findViewById(R.id.cancel_button);
        Button confirmButton = findViewById(R.id.confirm_button);

        cancelButton.setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> handleConfirm());
    }

    private void setupListeners() {
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.type_absolute) {
                absoluteContainer.setVisibility(View.VISIBLE);
                relativeContainer.setVisibility(View.GONE);
            } else {
                absoluteContainer.setVisibility(View.GONE);
                relativeContainer.setVisibility(View.VISIBLE);
            }
        });

        selectTimeButton.setOnClickListener(v -> showTimePicker());

        // Quick duration buttons
        findViewById(R.id.btn_5min).setOnClickListener(v -> setDuration(0, 5));
        findViewById(R.id.btn_15min).setOnClickListener(v -> setDuration(0, 15));
        findViewById(R.id.btn_30min).setOnClickListener(v -> setDuration(0, 30));
        findViewById(R.id.btn_1hour).setOnClickListener(v -> setDuration(1, 0));

        // Auto-advance from hours to minutes when 2 digits entered
        hoursInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // When 2 digits are entered, move to minutes input
                if (s.length() == 2) {
                    minutesInput.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Don't set any default values - inputs start empty
    }

    private void showTimePicker() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minuteOfHour) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minuteOfHour;
                    updateTimeButton();
                },
                hour,
                minute,
                true // 24-hour format
        );

        timePickerDialog.show();
    }

    private void updateTimeButton() {
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
        selectTimeButton.setText(timeStr);
    }

    private void setDuration(int hours, int minutes) {
        hoursInput.setText(hours > 0 ? String.valueOf(hours) : "");
        minutesInput.setText(minutes > 0 ? String.valueOf(minutes) : "");
    }

    private void handleConfirm() {
        try {
            String type;
            String time;
            long scheduledAt;

            int checkedId = typeGroup.getCheckedRadioButtonId();

            if (checkedId == R.id.type_absolute) {
                // Absolute time
                type = "absolute";
                time = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                scheduledAt = calculateAbsoluteTime(selectedHour, selectedMinute);
            } else {
                // Relative time
                type = "relative";

                String hoursStr = hoursInput.getText().toString().trim();
                String minutesStr = minutesInput.getText().toString().trim();

                int hours = hoursStr.isEmpty() ? 0 : Integer.parseInt(hoursStr);
                int minutes = minutesStr.isEmpty() ? 0 : Integer.parseInt(minutesStr);

                if (hours == 0 && minutes == 0) {
                    Toast.makeText(this, "Please enter a duration", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Format time string
                StringBuilder timeBuilder = new StringBuilder();
                if (hours > 0) {
                    timeBuilder.append(hours).append(" ").append(hours == 1 ? "hour" : "hours");
                }
                if (minutes > 0) {
                    if (timeBuilder.length() > 0) timeBuilder.append(" ");
                    timeBuilder.append(minutes).append(" ").append(minutes == 1 ? "minute" : "minutes");
                }
                time = timeBuilder.toString();

                scheduledAt = System.currentTimeMillis() + (hours * 60 * 60 * 1000L) + (minutes * 60 * 1000L);
            }

            // Update notification in storage
            updateNotification(notificationId, time, type, scheduledAt);

            scheduleAndroidNotification(notificationId, notificationName, scheduledAt);
            writeToLog("RESCHEDULE", notificationId, notificationName, scheduledAt);

            // Refresh all widgets
            refreshWidgets();

            Toast.makeText(this, "Notification rescheduled", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Log.e(TAG, "Error in handleConfirm", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private long calculateAbsoluteTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If time has passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        return calendar.getTimeInMillis();
    }

    private void updateNotification(String id, String time, String type, long scheduledAt) {
        try {
            SharedPreferences prefs = getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            String notificationsJson = prefs.getString("notifications", "[]");
            JSONArray array = new JSONArray(notificationsJson);

            // Find and update the notification
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (id.equals(obj.optString("id", ""))) {
                    obj.put("time", time);
                    obj.put("type", type);
                    obj.put("scheduledAt", scheduledAt);
                    obj.put("updatedAt", new Date().getTime());
                    obj.put("enabled", true);

                    // Update interval for relative notifications
                    if ("relative".equals(type)) {
                        String[] parts = time.toLowerCase().split(" ");
                        long totalMinutes = 0;
                        for (int j = 0; j < parts.length; j += 2) {
                            try {
                                int value = Integer.parseInt(parts[j]);
                                String unit = parts[j + 1];
                                if (unit.contains("hour")) {
                                    totalMinutes += value * 60;
                                } else if (unit.contains("minute")) {
                                    totalMinutes += value;
                                }
                            } catch (Exception ignored) {}
                        }
                        obj.put("interval", totalMinutes * 60 * 1000);
                    } else {
                        obj.remove("interval");
                    }

                    break;
                }
            }

            // Save back to preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("notifications", array.toString());
            editor.apply();

            Log.d(TAG, "✅ Updated SharedPreferences");

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update notification in storage", e);
            e.printStackTrace();
        }
    }

    // ✅ NEW METHOD: Schedule notification in Android AlarmManager
    private void scheduleAndroidNotification(String notificationId, String name, long scheduledAt) {
        try {
            Log.d(TAG, "📅 Scheduling in Android: " + name + " at " + new Date(scheduledAt));

            // Create intent for NotificationReceiver
            Intent notificationIntent = new Intent(this, NotificationReceiver.class);
            notificationIntent.putExtra("notificationId", notificationId);
            notificationIntent.putExtra("notificationName", name);

            // Generate numeric ID (same algorithm as app)
            int numericId = generateNumericId(notificationId);

            // Create pending intent
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    numericId,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Get AlarmManager
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

            if (alarmManager != null) {
                // Schedule the alarm with exact timing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledAt,
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            scheduledAt,
                            pendingIntent
                    );
                }

                Log.d(TAG, "✅ Notification scheduled in Android AlarmManager");
            } else {
                Log.e(TAG, "❌ AlarmManager is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to schedule Android notification", e);
        }
    }

    // ✅ NEW METHOD: Generate numeric ID from string ID (matches app logic)
    private int generateNumericId(String stringId) {
        int hash = 5381;
        for (int i = 0; i < stringId.length(); i++) {
            hash = ((hash << 5) + hash) ^ stringId.charAt(i);
        }
        return Math.abs(hash) % 2147483646 + 1;
    }

    // ✅ NEW METHOD: Write to debug log file
    private void writeToLog(String type, String id, String name, long scheduledAt) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            String timestamp = sdf.format(new Date());

            SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String scheduledTime = timeSdf.format(new Date(scheduledAt));

            String logLine = String.format("[%s] [%s] [ID:%s...] [%s] 🔄 Widget rescheduled for %s\n",
                    timestamp,
                    type,
                    id.substring(0, Math.min(12, id.length())),
                    name != null ? name : "Unnamed",
                    scheduledTime
            );

            // Write to same log file the app uses
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File logFile = new File(documentsDir, "notification_debug.log");

            FileWriter writer = new FileWriter(logFile, true); // append mode
            writer.write(logLine);
            writer.close();

            Log.d(TAG, "✅ Wrote to log file: " + logLine.substring(0, 50) + "...");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to write to log", e);
        }
    }

    private void refreshWidgets() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName thisWidget = new ComponentName(this, QuickNotifWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
            QuickNotifWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId);
        }
    }
}