package app.amir.quicknotif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import androidx.activity.OnBackPressedCallback;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.TimeUnit;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "QuickNotif";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppLogger.init(this);

        // Request exact alarm permission on Android 12+
        requestExactAlarmPermission();

        // Add JavaScript interface to allow web app to call native methods
        bridge.getWebView().addJavascriptInterface(new WebAppInterface(), "Android");

        // Move app to background on back press instead of closing it
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        // Register periodic watchdog that reschedules any alarms cleared by the OS
        PeriodicWorkRequest watchdog = new PeriodicWorkRequest.Builder(
                AlarmWatchdogWorker.class, 60, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "alarm_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                watchdog);
    }

    public class WebAppInterface {

        @JavascriptInterface
        public boolean isBatteryOptimized() {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                return !pm.isIgnoringBatteryOptimizations(getPackageName());
            }
            return false;
        }

        @JavascriptInterface
        public void openBatterySettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                openAppSettings();
            }
        }

        @JavascriptInterface
        public boolean openAutoStartSettings() {
            String manufacturer = Build.MANUFACTURER.toLowerCase();

            try {
                Intent intent = new Intent();

                switch (manufacturer) {
                    case "xiaomi":
                    case "redmi":
                        intent.setComponent(new ComponentName("com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                        break;

                    case "oppo":
                        try {
                            intent.setComponent(new ComponentName("com.coloros.safecenter",
                                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                        } catch (Exception e) {
                            intent.setComponent(new ComponentName("com.oppo.safe",
                                    "com.oppo.safe.permission.startup.StartupAppListActivity"));
                        }
                        break;

                    case "vivo":
                        intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                        break;

                    case "huawei":
                    case "honor":
                        intent.setComponent(new ComponentName("com.huawei.systemmanager",
                                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                        break;

                    case "samsung":
                        openBatterySettings();
                        return true;

                    case "oneplus":
                        try {
                            intent.setComponent(new ComponentName("com.oneplus.security",
                                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                        } catch (Exception e) {
                            intent.setComponent(new ComponentName("com.coloros.safecenter",
                                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                        }
                        break;

                    case "asus":
                        intent.setComponent(new ComponentName("com.asus.mobilemanager",
                                "com.asus.mobilemanager.autostart.AutoStartActivity"));
                        break;

                    case "letv":
                        intent.setComponent(new ComponentName("com.letv.android.letvsafe",
                                "com.letv.android.letvsafe.AutobootManageActivity"));
                        break;

                    default:
                        return false;
                }

                startActivity(intent);
                return true;

            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void openAppSettings() {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                AppLogger.e(TAG,"Failed to open app settings", e);
            }
        }

        @JavascriptInterface
        public boolean isAlarmScheduled(int notificationId) {
            try {
                Intent intent = new Intent(MainActivity.this, NotificationReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        MainActivity.this,
                        notificationId,
                        intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                return pendingIntent != null;
            } catch (Exception e) {
                AppLogger.e(TAG,"Error checking alarm: " + e.getMessage());
                return false;
            }
        }

        @JavascriptInterface
        public String checkAllAlarms(String notificationIdsJson) {
            try {
                JSONArray ids = new JSONArray(notificationIdsJson);
                JSONArray scheduled = new JSONArray();

                for (int i = 0; i < ids.length(); i++) {
                    int notifId = ids.getInt(i);
                    if (isAlarmScheduled(notifId)) {
                        scheduled.put(notifId);
                    }
                }

                return scheduled.toString();
            } catch (JSONException e) {
                AppLogger.e(TAG,"Error checking alarms: " + e.getMessage());
                return "[]";
            }
        }

        @JavascriptInterface
        public void cancelAlarmManagerNotification(String notificationId) {
            try {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager == null) {
                    AppLogger.e(TAG,"AlarmManager is null");
                    return;
                }

                Intent notificationIntent = new Intent(MainActivity.this, NotificationReceiver.class);
                notificationIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_ID, notificationId);
                notificationIntent.putExtra(NotifUtils.EXTRA_NOTIFICATION_NAME, "");

                int numericId = NotifUtils.generateNumericId(notificationId);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        MainActivity.this,
                        numericId,
                        notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                alarmManager.cancel(pendingIntent);
                AppLogger.d(TAG,"✅ Canceled AlarmManager alarm for ID: " + notificationId
                        + " (numeric: " + numericId + ")");
            } catch (Exception e) {
                AppLogger.e(TAG,"❌ Error canceling AlarmManager alarm: " + e.getMessage(), e);
            }
        }

        @JavascriptInterface
        public void refreshWidget() {
            NotifUtils.refreshAllWidgets(MainActivity.this);
        }

        @JavascriptInterface
        public boolean canScheduleExactAlarms() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                return alarmManager != null && alarmManager.canScheduleExactAlarms();
            }
            return true; // Pre-Android 12: always allowed
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                bridge.getWebView().evaluateJavascript(
                    "if(window.onExactAlarmPermissionMissing) window.onExactAlarmPermissionMissing();",
                    null);
            }
        }
    }

    /** Prompts the user to grant exact-alarm permission on Android 12+. */
    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }
}
