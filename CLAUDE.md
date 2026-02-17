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

**Android Development:**
```bash
# CRITICAL: After making ANY changes to the project, run this sequence:
npm run build                    # Build web assets
npx cap sync android            # Sync to Android project
cd android && ./gradlew assembleDebug  # Create APK
cp android/app/build/outputs/apk/debug/app-debug.apk QuickNotif-latest.apk  # Copy APK to root
```

**Important:** ALWAYS run the full build sequence (`npm run build` → `npx cap sync android` → `gradlew assembleDebug` → `cp APK to root`) after making changes. This ensures:
1. Web assets are built with latest code
2. Changes are synced to the Android project
3. A new APK is generated for testing
4. The latest APK is copied to the root folder as `QuickNotif-latest.apk` for easy access

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
- Refresh: Updates widget display (polls every 5 minutes via AlarmManager)
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

## Important Notes

- **Never change the hash algorithm** in `toNumericId()` / `generateNumericId()` - it would break existing scheduled notifications
- **Always sync storage format** between TypeScript and Java - both parse the same JSON structure
- **Test on different manufacturers** - auto-start and battery settings vary significantly
- **Widget actions are fire-and-forget** - no async callbacks, must read from storage to verify changes
