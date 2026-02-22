package app.amir.quicknotif;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import java.util.Calendar;
import java.util.Locale;

/**
 * Shared base for AddNotificationActivity and RescheduleActivity.
 * Owns all common views, listeners, and schedule-computation helpers.
 */
public abstract class BaseNotificationActivity extends Activity {

    protected RadioGroup    typeGroup;
    protected LinearLayout  absoluteContainer;
    protected LinearLayout  relativeContainer;
    protected Button        selectTimeButton;
    protected EditText      hoursInput;
    protected EditText      minutesInput;

    protected int selectedHour   = 12;
    protected int selectedMinute = 0;

    /** Each subclass provides its own layout resource. */
    protected abstract int getLayoutId();

    /** Called when the confirm button is pressed. */
    protected abstract void handleConfirm();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(getLayoutId());
        initializeCommonViews();
        setupCommonListeners();
        onAfterViewsInitialized();
    }

    /**
     * Template method called after common views are ready.
     * Subclasses override this to do layout-specific setup
     * (e.g. finding nameInput, reading intent extras, setting title).
     */
    protected void onAfterViewsInitialized() {}

    private void initializeCommonViews() {
        typeGroup          = findViewById(R.id.type_group);
        absoluteContainer  = findViewById(R.id.absolute_container);
        relativeContainer  = findViewById(R.id.relative_container);
        selectTimeButton   = findViewById(R.id.select_time_button);
        hoursInput         = findViewById(R.id.hours_input);
        minutesInput       = findViewById(R.id.minutes_input);

        Button cancelButton  = findViewById(R.id.cancel_button);
        Button confirmButton = findViewById(R.id.confirm_button);
        cancelButton.setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> handleConfirm());
    }

    private void setupCommonListeners() {
        // Switch between absolute and relative time pickers
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

        // Allow quick selection of relative times
        findViewById(R.id.btn_5min).setOnClickListener(v -> setDuration(0, 5));
        findViewById(R.id.btn_15min).setOnClickListener(v -> setDuration(0, 15));
        findViewById(R.id.btn_30min).setOnClickListener(v -> setDuration(0, 30));
        findViewById(R.id.btn_1hour).setOnClickListener(v -> setDuration(1, 0));

        // Auto-advance from hours to minutes when 2 digits entered
        hoursInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 2) minutesInput.requestFocus();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    protected void showTimePicker() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(
                this,
                (view, hourOfDay, minuteOfHour) -> {
                    selectedHour   = hourOfDay;
                    selectedMinute = minuteOfHour;
                    updateTimeButton();
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true // 24-hour format
        ).show();
    }

    protected void updateTimeButton() {
        selectTimeButton.setText(
                String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute));
    }

    protected void setDuration(int hours, int minutes) {
        hoursInput.setText(hours > 0 ? String.valueOf(hours) : "");
        minutesInput.setText(minutes > 0 ? String.valueOf(minutes) : "");
    }

    /** Returns the next occurrence of the given HH:mm time (today or tomorrow). */
    protected long calculateAbsoluteTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        return calendar.getTimeInMillis();
    }

    /** Builds a readable string like "1 hour 30 minutes". */
    protected String buildTimeString(int hours, int minutes) {
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" ").append(hours == 1 ? "hour" : "hours");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append(" ").append(minutes == 1 ? "minute" : "minutes");
        }
        return sb.toString();
    }

    /** Returns the duration in milliseconds for the given hours + minutes. */
    protected long calculateRelativeMs(int hours, int minutes) {
        if (hours < 0 || minutes < 0 || (hours == 0 && minutes == 0)) {
            throw new IllegalArgumentException("Duration must be at least 1 minute (got " + hours + "h " + minutes + "m)");
        }
        return (hours * 3600L + minutes * 60L) * 1000L;
    }
}
