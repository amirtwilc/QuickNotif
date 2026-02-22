package app.amir.quicknotif;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Debug file logger. In debug builds only, mirrors every log call to
 * {@code <external-files-dir>/quicknotif.log} in addition to logcat.
 *
 * The file is capped at {@link #MAX_LOG_SIZE_BYTES}; when exceeded it is
 * cleared and a rotation marker is written so the file never grows unbounded.
 *
 * Retrieve from the device:
 *   - File manager: Internal Storage / Android / data / app.amir.quicknotif / files / quicknotif.log
 *   - ADB:          adb pull /sdcard/Android/data/app.amir.quicknotif/files/quicknotif.log
 *
 * {@link #init(Context)} must be called once from each Android component entry
 * point (Activity.onCreate, BroadcastReceiver.onReceive, Worker.doWork) so
 * that the logger is ready even when MainActivity has not been launched.
 * The method is idempotent — subsequent calls are no-ops.
 */
public final class AppLogger {

    /** Maximum file size before the log is rotated (cleared). Adjust as needed. */
    private static final long MAX_LOG_SIZE_BYTES = 512 * 1024L; // 512 KB

    private static final String LOG_FILE_NAME = "notification_debug.log";

    private static volatile Context appContext;
    private static volatile boolean debugMode = false;

    private AppLogger() {}

    /** Idempotent. Safe to call from every component entry point. No-op in release builds. */
    public static void init(Context context) {
        if (appContext == null) {
            Context ctx = context.getApplicationContext();
            debugMode = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (debugMode) {
                appContext = ctx;
            }
        }
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        writeToFile("D", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        writeToFile("W", tag, msg, null);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        writeToFile("E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
        writeToFile("E", tag, msg, t);
    }

    private static void writeToFile(String level, String tag, String msg, Throwable t) {
        if (!debugMode || appContext == null) return;

        try {
            File logFile = getLogFile();
            if (logFile == null) return;

            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                try (FileWriter fw = new FileWriter(logFile, false)) {
                    fw.write("[LOG ROTATED at " + now() + "]\n");
                }
            }

            String stackTrace = t != null ? "\n    " + Log.getStackTraceString(t).trim() : "";
            String line = String.format("[%s] %s/%s: %s%s\n", now(), level, tag, msg, stackTrace);

            try (FileWriter fw = new FileWriter(logFile, true)) {
                fw.write(line);
            }
        } catch (Exception ignored) {
            // Never call AppLogger from here — avoid any chance of recursion
        }
    }

    private static File getLogFile() {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) return null;
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new File(dir, LOG_FILE_NAME);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
