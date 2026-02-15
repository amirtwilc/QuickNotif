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
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONException;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestExactAlarmPermission();
        }

        // Add JavaScript interface to allow web app to call native methods
        bridge.getWebView().addJavascriptInterface(new WebAppInterface(), "Android");
    }

    @Override
    public void onBackPressed() {
        // Move app to background instead of closing it
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public class WebAppInterface {

        @JavascriptInterface
        public boolean isBatteryOptimized() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    return !pm.isIgnoringBatteryOptimizations(getPackageName());
                }
            }
            return false; // For older versions, assume it's fine
        }

        @JavascriptInterface
        public void openBatterySettings() {
            Intent intent = new Intent();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For Android 6.0 and above, open battery optimization settings
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));

                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // If the specific battery optimization settings fail, try general battery settings
                    try {
                        intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception ex) {
                        // Fallback to app settings if battery settings are not available
                        openAppSettings();
                    }
                }
            } else {
                // For older Android versions, just open app settings
                openAppSettings();
            }
        }

        @JavascriptInterface
        public boolean openAutoStartSettings() {
            String manufacturer = Build.MANUFACTURER.toLowerCase();

            try {
                Intent intent = new Intent();

                // Different manufacturers have different auto-start settings
                switch (manufacturer) {
                    case "xiaomi":
                    case "redmi":
                        // Xiaomi/Redmi auto-start settings
                        intent.setComponent(new ComponentName("com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                        break;

                    case "oppo":
                        // OPPO auto-start settings
                        try {
                            intent.setComponent(new ComponentName("com.coloros.safecenter",
                                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                        } catch (Exception e) {
                            intent.setComponent(new ComponentName("com.oppo.safe",
                                    "com.oppo.safe.permission.startup.StartupAppListActivity"));
                        }
                        break;

                    case "vivo":
                        // Vivo auto-start settings
                        intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                        break;

                    case "huawei":
                    case "honor":
                        // Huawei/Honor auto-start settings
                        intent.setComponent(new ComponentName("com.huawei.systemmanager",
                                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                        break;

                    case "samsung":
                        // Samsung - doesn't have traditional auto-start, open battery settings instead
                        openBatterySettings();
                        return true;

                    case "oneplus":
                        // OnePlus auto-start settings
                        try {
                            intent.setComponent(new ComponentName("com.oneplus.security",
                                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                        } catch (Exception e) {
                            // Fallback for older OnePlus
                            intent.setComponent(new ComponentName("com.coloros.safecenter",
                                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                        }
                        break;

                    case "asus":
                        // ASUS auto-start settings
                        intent.setComponent(new ComponentName("com.asus.mobilemanager",
                                "com.asus.mobilemanager.autostart.AutoStartActivity"));
                        break;

                    case "letv":
                        // LeTV auto-start settings
                        intent.setComponent(new ComponentName("com.letv.android.letvsafe",
                                "com.letv.android.letvsafe.AutobootManageActivity"));
                        break;

                    default:
                        // For stock Android and unknown manufacturers, return false
                        return false;
                }

                startActivity(intent);
                return true;

            } catch (Exception e) {
                // If manufacturer-specific settings don't work, return false
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
                e.printStackTrace();
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
                Log.e("QuickNotif", "Error checking alarm: " + e.getMessage());
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
                Log.e("QuickNotif", "Error checking alarms: " + e.getMessage());
                return "[]";
            }
        }

        /**
         * Cancel an AlarmManager alarm directly (for alarms scheduled by widget)
         * This is necessary because LocalNotifications.cancel() can't cancel alarms
         * that were scheduled directly via AlarmManager
         */
        @JavascriptInterface
        public void cancelAlarmManagerNotification(String notificationId) {
            try {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager == null) {
                    Log.e("QuickNotif", "AlarmManager is null");
                    return;
                }

                // Create the same PendingIntent as used by widget scheduling
                Intent notificationIntent = new Intent(MainActivity.this, NotificationReceiver.class);
                notificationIntent.putExtra("notificationId", notificationId);
                notificationIntent.putExtra("notificationName", ""); // Name doesn't matter for matching

                int numericId = generateNumericId(notificationId);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        MainActivity.this,
                        numericId,
                        notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                // Cancel the alarm
                alarmManager.cancel(pendingIntent);
                Log.d("QuickNotif", "✅ Canceled AlarmManager alarm for ID: " + notificationId + " (numeric: " + numericId + ")");
            } catch (Exception e) {
                Log.e("QuickNotif", "❌ Error canceling AlarmManager alarm: " + e.getMessage(), e);
            }
        }

        /**
         * Generate consistent numeric ID from string ID (same algorithm as widget)
         */
        private int generateNumericId(String stringId) {
            int hash = 5381;
            for (int i = 0; i < stringId.length(); i++) {
                hash = ((hash << 5) + hash) ^ stringId.charAt(i);
            }
            return Math.abs(hash) % 2147483646 + 1;
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestExactAlarmPermission() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            startActivity(intent);
        }
    }
}