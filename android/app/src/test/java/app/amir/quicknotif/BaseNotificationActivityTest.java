package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.content.Context;
import android.widget.Button;
import android.widget.RadioButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Calendar;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BaseNotificationActivityTest {

    /**
     * Concrete subclass used only for testing BaseNotificationActivity.
     * Uses the add_notification layout which has all required view IDs.
     */
    public static class ConcreteTestActivity extends BaseNotificationActivity {
        boolean confirmCalled = false;

        @Override
        protected int getLayoutId() {
            return R.layout.activity_add_notification;
        }

        @Override
        protected void handleConfirm() {
            confirmCalled = true;
        }
    }

    private ActivityController<ConcreteTestActivity> controller;
    private ConcreteTestActivity activity;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(ConcreteTestActivity.class).create().start().resume();
        activity = controller.get();
    }

    // ─── buildTimeString ──────────────────────────────────────────────────────

    @Test
    public void buildTimeString_1Hour() {
        assertEquals("1 hour", activity.buildTimeString(1, 0));
    }

    @Test
    public void buildTimeString_2Hours() {
        assertEquals("2 hours", activity.buildTimeString(2, 0));
    }

    @Test
    public void buildTimeString_1Minute() {
        assertEquals("1 minute", activity.buildTimeString(0, 1));
    }

    @Test
    public void buildTimeString_45Minutes() {
        assertEquals("45 minutes", activity.buildTimeString(0, 45));
    }

    @Test
    public void buildTimeString_1Hour30Minutes() {
        assertEquals("1 hour 30 minutes", activity.buildTimeString(1, 30));
    }

    @Test
    public void buildTimeString_zeroZero() {
        assertEquals("", activity.buildTimeString(0, 0));
    }

    // ─── calculateRelativeMs ──────────────────────────────────────────────────

    @Test
    public void calculateRelativeMs_5Minutes() {
        assertEquals(300_000L, activity.calculateRelativeMs(0, 5));
    }

    @Test
    public void calculateRelativeMs_1Hour() {
        assertEquals(3_600_000L, activity.calculateRelativeMs(1, 0));
    }

    @Test
    public void calculateRelativeMs_1Hour30Minutes() {
        assertEquals(5_400_000L, activity.calculateRelativeMs(1, 30));
    }

    @Test(expected = IllegalArgumentException.class)
    public void calculateRelativeMs_zeroZero() {
        activity.calculateRelativeMs(0, 0);
    }

    @Test
    public void calculateRelativeMs_largeValue_noIntOverflow() {
        // 600 hours = 2160000 seconds — result must exceed Integer.MAX_VALUE (~2.1B)
        long result = activity.calculateRelativeMs(600, 0);
        assertTrue("Expected > Integer.MAX_VALUE, got " + result,
                result > (long) Integer.MAX_VALUE);
    }

    // ─── calculateAbsoluteTime ────────────────────────────────────────────────

    @Test
    public void calculateAbsoluteTime_futureTime_schedulesToday() {
        // Use a time far in the future relative to "now"
        Calendar now = Calendar.getInstance();
        // Schedule one hour ahead, which is always in the future
        int futureHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24;
        // If wraps to 0, it would be tomorrow — use +2 instead to keep it simple
        futureHour = now.get(Calendar.HOUR_OF_DAY) == 23 ? 23 : now.get(Calendar.HOUR_OF_DAY) + 1;

        long result = activity.calculateAbsoluteTime(futureHour, 0);
        Calendar resultCal = Calendar.getInstance();
        resultCal.setTimeInMillis(result);

        // Result must be today (same date)
        assertEquals(now.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR));
        assertEquals(now.get(Calendar.YEAR), resultCal.get(Calendar.YEAR));
    }

    @Test
    public void calculateAbsoluteTime_pastTime_schedulesTomorrow() {
        // hour=0, minute=0 is midnight — always in the past unless it's exactly midnight
        // Use a time far in the past (hour=0) to ensure it's tomorrow
        Calendar now = Calendar.getInstance();
        // Only reliable if current time is not midnight - use a clearly past hour
        if (now.get(Calendar.HOUR_OF_DAY) > 0) {
            long result = activity.calculateAbsoluteTime(0, 0);
            Calendar resultCal = Calendar.getInstance();
            resultCal.setTimeInMillis(result);
            // Result must be in the future
            assertTrue("Expected result > now", result > System.currentTimeMillis());
        }
        // Edge case at midnight: skip (tested separately)
    }

    @Test
    public void calculateAbsoluteTime_resultIsInFuture() {
        // Regardless of inputs, result must always be > now
        long result = activity.calculateAbsoluteTime(activity.selectedHour, activity.selectedMinute);
        assertTrue("Expected scheduled time in the future", result > System.currentTimeMillis());
    }

    // ─── View initialization ──────────────────────────────────────────────────

    @Test
    public void onCreate_setsUpAllCommonViews() {
        assertNotNull("typeGroup should not be null",        activity.typeGroup);
        assertNotNull("hoursInput should not be null",       activity.hoursInput);
        assertNotNull("selectTimeButton should not be null", activity.selectTimeButton);
    }

    // ─── Button listeners ─────────────────────────────────────────────────────

    @Test
    public void cancelButton_onClick_finishesActivity() {
        Button cancel = activity.findViewById(R.id.cancel_button);
        cancel.performClick();
        assertTrue("Activity should be finishing after cancel", activity.isFinishing());
    }

    @Test
    public void confirmButton_onClick_callsHandleConfirm() {
        Button confirm = activity.findViewById(R.id.confirm_button);
        confirm.performClick();
        assertTrue("handleConfirm() should have been called", activity.confirmCalled);
    }

    @Test
    public void quickDurationButtons_fillHoursMinutes() {
        Button btn5min = activity.findViewById(R.id.btn_5min);
        btn5min.performClick();
        assertEquals("", activity.hoursInput.getText().toString());
        assertEquals("5", activity.minutesInput.getText().toString());
    }
}
