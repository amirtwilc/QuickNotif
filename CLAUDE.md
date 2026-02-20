# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

QuickNotif is a Capacitor-based Android notification scheduler app with a home screen widget. Users can schedule notifications at specific times or after durations, and interact with them directly from the Android widget.

**Tech Stack:**
- Frontend: React 18, TypeScript, Vite, TailwindCSS, shadcn/ui
- Mobile: Capacitor 7 for Android
- Widget: Native Android AppWidget with RemoteViews
- State: Capacitor Preferences API (SharedPreferences on Android)

## Build & Development Commands

**Web Development:**
```bash
npm run dev          # Start Vite dev server
npm run build        # Production build
npm run build:dev    # Development build with source maps
npm run lint         # Run ESLint
npm run preview      # Preview production build
```

**Testing:**
```bash
npm test                 # Run all React/TypeScript tests (required before every APK build)
npm run test:watch       # Run tests in watch mode during development
npm run test:coverage    # Run tests with coverage report

# Android unit tests (Robolectric) — required before every APK build
# IMPORTANT: Must use JDK 21 — system default Java 25 is incompatible with Gradle 8.11.1
export JAVA_HOME="C:/Program Files/Java/jdk-21"
cd android && ./gradlew :app:testDebugUnitTest
```

**Android Development:**
```bash
# CRITICAL: After making ANY changes to the project, run this sequence:
npm test                                                    # 1. React/TS tests — fix failures before proceeding
export JAVA_HOME="C:/Program Files/Java/jdk-21"            # 2. Set JDK 21 (required for Gradle)
cd android && ./gradlew :app:testDebugUnitTest && cd ..    # 3. Android unit tests — fix failures before proceeding
npm run build                                               # 4. Build web assets
npx cap sync android                                        # 5. Sync to Android project
cd android && ./gradlew assembleDebug                       # 6. Create APK
cp android/app/build/outputs/apk/debug/app-debug.apk QuickNotif-latest.apk  # 7. Copy APK to root
```

**Important:** ALWAYS run the full build sequence after making changes. This ensures:
1. React/TypeScript tests pass — no regressions in frontend logic
2. Android Java tests pass — no regressions in widget/alarm/receiver logic
3. Web assets are built with latest code
4. Changes are synced to the Android project
5. A new APK is generated for testing
6. The latest APK is copied to the root folder as `QuickNotif-latest.apk` for easy access

**CRITICAL:** Never build the APK if either `npm test` or `gradlew testDebugUnitTest` fails. Fix failing tests first.

**JDK Note:** The system default Java is version 25, which Gradle 8.11.1 does not support. Always set `JAVA_HOME="C:/Program Files/Java/jdk-21"` before running any `./gradlew` command.

## Architecture

### Data Flow & State Management

The app uses a **shared state model** between the React app and Android widget:

1. **React App → Storage:** `NotificationService` saves notifications to Capacitor Preferences (which uses Android SharedPreferences with key "CapacitorStorage")
2. **Widget → Storage:** Widget reads from the same SharedPreferences to display notifications
3. **Widget Actions → Storage:** Widget directly modifies SharedPreferences and schedules Android alarms
4. **Both sides independently schedule:** Both TypeScript and Java can schedule Android AlarmManager notifications using the same numeric ID generation algorithm

### Notification ID System

**Critical:** Notification IDs must be consistent across TypeScript and Java.

- TypeScript generates string IDs: `notification_${Date.now()}_${random}`
- Both sides convert to numeric IDs using **identical hash algorithm** (DJB2 variant)
- See `toNumericId()` in `notificationService.ts:30` and `generateNumericId()` in `QuickNotifWidgetProvider.java:387`

### Notification Types

- **Absolute:** Specific time of day (e.g., "14:30"). Schedules for next occurrence (today or tomorrow).
- **Relative:** Duration from now (e.g., "1 hour 30 minutes"). Stored with `interval` field in milliseconds for reactivation.

### Permission Flow

Multi-step permission setup (see `Index.tsx:70-119`):
1. **Notification Permission:** Standard Android notification permission
2. **Battery Optimization:** Disable battery optimization for exact alarms
3. **Auto-Start Permission:** Manufacturer-specific (Xiaomi, OPPO, etc.) - see `MainActivity.java:84-164`
4. **Exact Alarm Permission:** Android 12+ requirement (requested in `MainActivity.onCreate`)

### Widget System

**Components:**
- `QuickNotifWidgetProvider.java`: AppWidgetProvider that handles widget lifecycle, refresh, and actions
- `QuickNotifWidgetService.java`: RemoteViewsService that provides data for the widget's ListView
- `NotificationReceiver.java`: BroadcastReceiver for actual notification display
- `RescheduleActivity.java`: Dialog activity for rescheduling notifications from widget
- `BootReceiver.java`: Restores alarms after device reboot

**Widget Actions:**
- Refresh: Updates widget display (polls every minute via AlarmManager)
- Delete: Removes notification from storage
- Reactivate: Re-schedules notification using original time/interval
- Reschedule: Opens dialog to change notification time

**Widget Data Loading:**
- Widget reads from `CapacitorStorage` SharedPreferences directly
- Parses `notifications` JSON array
- Sorts by: expired first, then by scheduled time
- Displays only enabled notifications

### JavaScript Bridge

`MainActivity.java` exposes native APIs to web via `window.Android`:

```typescript
// TypeScript calls these native methods
window.Android.isBatteryOptimized(): boolean
window.Android.openBatterySettings(): void
window.Android.openAutoStartSettings(): boolean
window.Android.openAppSettings(): void
```

Used by `NotificationService.ts` to guide users through permission setup.

## Key Files

**TypeScript/React:**
- `src/services/notificationService.ts`: Core notification scheduling service
- `src/services/notificationLogger.ts`: Debug logging to device storage
- `src/pages/Index.tsx`: Main app page with permission flow
- `src/components/NotificationList.tsx`: Displays scheduled notifications
- `src/components/TimeInput.tsx`: Time picker for absolute/relative times
- `capacitor.config.ts`: Capacitor configuration (app ID, plugins, Android settings)

**Android/Java:**
- `android/app/src/main/java/app/amir/quicknotif/`:
  - `QuickNotifWidgetProvider.java`: Widget provider and action handler
  - `QuickNotifWidgetService.java`: Widget data source
  - `MainActivity.java`: WebView bridge for permissions
  - `NotificationReceiver.java`: Displays notifications when triggered
  - `RescheduleActivity.java`: Widget reschedule dialog
  - `BootReceiver.java`: Boot completion handler
- `android/app/src/main/AndroidManifest.xml`: Permissions and component declarations

## Debugging

**Enable debug mode:** Create `notification_debug.log` in device Documents folder. The app will detect it and show debug UI.

**View logs:**
- Widget writes to `Documents/notification_debug.log`
- TypeScript service also writes to same file
- Use `DebugLogViewer.tsx` component in app to view logs

**Common issues:**
- **Notification not firing:** Check battery optimization, exact alarm permission, and that AlarmManager was called with correct timestamp
- **Widget not updating:** Verify SharedPreferences key is "CapacitorStorage" and JSON is valid
- **ID mismatch:** Ensure both TypeScript and Java use same ID generation algorithm
- **Time zone issues:** Use local time for display, but store as UTC timestamp (milliseconds since epoch)

## Testing

- **Web preview:** `npm run dev` (limited - no native features)
- **Android device:** `npx cap sync android && npx cap run android`
- **Widget testing:** Add widget to home screen, verify CRUD operations work
- **Permission testing:** Fresh install to test permission flow
- **Reboot testing:** Schedule notification, reboot device, verify it reschedules via BootReceiver

**Android unit tests (Robolectric):**
- Test files: `android/app/src/test/java/app/amir/quicknotif/`
- Run with: `export JAVA_HOME="C:/Program Files/Java/jdk-21" && cd android && ./gradlew :app:testDebugUnitTest`
- Covers: `NotifUtils`, `BaseNotificationActivity`, `BootReceiver`, `QuickNotifWidgetProvider`, `QuickNotifWidgetService`, `AddNotificationActivity`, `RescheduleActivity`, `NotificationReceiver`
- 112 tests covering ID hashing, alarm scheduling, SharedPreferences CRUD, widget actions, and notification display
- **Coverage:** `enableUnitTestCoverage = true` (AGP) + Robolectric produces a 0% HTML report due to AGP offline instrumentation being incompatible with Robolectric's classloader. The `.exec` data file is real but the report is unusable. To get accurate coverage, the `jacoco` Gradle plugin with online instrumentation is needed instead.

## Important Notes

- **Never change the hash algorithm** in `toNumericId()` / `generateNumericId()` - it would break existing scheduled notifications
- **Always sync storage format** between TypeScript and Java - both parse the same JSON structure
- **Test on different manufacturers** - auto-start and battery settings vary significantly
- **Widget actions are fire-and-forget** - no async callbacks, must read from storage to verify changes
