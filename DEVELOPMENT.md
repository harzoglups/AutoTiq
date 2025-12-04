# FairLaunch - Development Notes

## Quick Start for New Session

### Build and Installation
```bash
# Build with Android Studio Java
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug

# Install on device
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
~/Library/Android/sdk/platform-tools/adb shell am start -n com.fairlaunch/.MainActivity
```

### Debugging
```bash
# View Worker logs
~/Library/Android/sdk/platform-tools/adb logcat | grep LocationCheckWorker

# View boot and worker logs
~/Library/Android/sdk/platform-tools/adb logcat | grep -E "(BootReceiver|LocationCheckWorker)"

# Clear and view logs
~/Library/Android/sdk/platform-tools/adb logcat -c && ~/Library/Android/sdk/platform-tools/adb logcat | grep LocationCheckWorker

# Check if device is connected
~/Library/Android/sdk/platform-tools/adb devices

# Check GPS/location status
~/Library/Android/sdk/platform-tools/adb shell settings get secure location_providers_allowed

# Check app permissions
~/Library/Android/sdk/platform-tools/adb shell dumpsys package com.fairlaunch | grep -A 5 "granted=true"

# Check WorkManager diagnostics
~/Library/Android/sdk/platform-tools/adb shell am broadcast -a "androidx.work.diagnostics.REQUEST_DIAGNOSTICS" -p com.fairlaunch
```

## Important Technical Decisions

### 1. Fairtiq Package Name
- **Package used**: `com.fairtiq.android` (verified and working)
- **Location**: `LocationCheckWorker.kt` line 34
- Tested with: `adb shell monkey -p com.fairtiq.android -c android.intent.category.LAUNCHER 1`

### 2. Check Intervals
- **Format**: Seconds (to ease testing)
- **Default**: 300 seconds (5 minutes)
- **WorkManager limitation**: Minimum 15 minutes for PeriodicWork
- **Solution**: For intervals < 900s, uses OneTimeWorkRequest with auto-rescheduling
- **Location**: `LocationWorkScheduler.kt` and `LocationCheckWorker.rescheduleIfNeeded()`

### 3. Hilt + WorkManager
- **Required configuration**:
  - `FairLaunchApplication` implements `Configuration.Provider`
  - `HiltWorkerFactory` injection
  - Disable WorkManager auto-init in `AndroidManifest.xml`
- **Location**: `FairLaunchApplication.kt`, `AndroidManifest.xml` (meta-data with `tools:node="remove"`)

### 4. Distance Calculation
- **Method**: Haversine formula (pure Kotlin)
- **Reason**: Avoid Android dependency in domain layer
- **Location**: `CheckProximityUseCase.calculateDistance()`

### 5. Anti-Spam Proximity
- **Mechanism**: Store state (inside/outside) per point in Room
- **Trigger**: Only on outside → inside transition
- **Database**: Table `proximity_states` with `point_id` and `is_inside`

### 6. Map and Location
- **Auto-centering**: `locationOverlay.runOnFirstFix` + `controller.animateTo()` (only on first GPS fix)
- **Follow mode**: Disabled - user has full manual control of map navigation
- **Initial zoom**: 15 (closer)
- **Proximity circles**: Red semi-transparent circles displayed on map for each point
- **Map layers**: User can switch between Street and Topographic views via floating button (top-right)
- **Layer persistence**: Selected layer saved in DataStore preferences
- **UI Style**: Full-screen edge-to-edge map with floating action buttons (Google Maps style)
  - Settings button: bottom-right floating button
  - GPS centering button: bottom-right floating button (above settings)
  - Layer selection: top-right floating button (below search bar)
  - No top app bar for immersive experience
- **Location search**: 
  - Search bar at top center (90% width)
  - Photon geocoding API (better partial/fuzzy search than Nominatim)
  - Prioritizes results near visible map area using viewbox biasing
  - 300ms debounce, minimum 3 characters
  - Keeps previous results when search returns empty
  - Red pin marker on searched locations (distinct from blue user markers)
  - Search marker disappears on map click or new search
- **Marker interaction**: Custom touch handling at map level distinguishes short/long press
  - Long press on map (500ms) → Add marker (opens edit dialog automatically)
  - Long press on marker (500ms) → Delete marker
  - Short click on marker → Show info card with details
  - Touch zone: 100px radius around markers for easy interaction
- **Marker properties**: Editable name and time window (HH:MM precision)
  - Edit dialog with scroll pickers for hour and minute selection
  - Time window determines when point can trigger Fairtiq launch
  - Active time displayed in info card (e.g., "Active: 08:30 - 18:45")

### 7. Boot Auto-Start
- **BootReceiver**: Triggers on `ACTION_BOOT_COMPLETED`
- **Delay**: 30 seconds initial delay to allow GPS initialization (cold start)
- **GPS timeout**: 30 seconds for first location request after boot
- **Verification**: Checks DataStore file existence to avoid premature initialization
- **Location**: `BootReceiver.kt` with 30s `setInitialDelay()`

### 8. Notifications and Alerts
- **Full-screen intent**: Launches Fairtiq and wakes screen even when locked
- **Direct vibration**: Strong 3-burst pattern (500ms each) at max amplitude
- **Permission flow**: Requests POST_NOTIFICATIONS on first launch (Android 13+)
- **Channel**: "Alertes de proximité" with HIGH importance
- **Location**: `LocationCheckWorker.launchFairtiqAndVibrate()` and `MainActivity.requestNotificationPermissionIfNeeded()`

### 9. App Icon
- **Style**: Custom vector drawable, red background with white rounded "L"
- **Color**: Red (#E53935) - distinctive and visible
- **Design**: Smaller "L" with rounded corners (Q-like style)
- **Format**: Adaptive icon for Android 8+ with fallback for older versions
- **Location**: `drawable/ic_launcher_*` files

## Data Structure

### DataStore (Settings)
```kotlin
check_interval_seconds: Int = 300    // Changed from minutes to seconds
proximity_distance_meters: Int = 200
location_tracking_enabled: Boolean = false
map_layer_type: String = "STREET"   // STREET, TOPO
active_weekdays: String = "1,2,3,4,5,6,7"  // CSV: 1=Monday, 7=Sunday
```

### Room Database
**Table: map_points**
- id (PK, auto-increment)
- latitude (Double)
- longitude (Double)
- name (String)
- start_hour (Int, 0-23)
- start_minute (Int, 0-59)
- end_hour (Int, 0-23)
- end_minute (Int, 0-59)
- created_at (Long)

**Table: proximity_states**
- point_id (PK, FK → map_points)
- is_inside (Boolean)

## Points of Attention

### For Production
1. **Permissions**: Implement 2-step flow for BACKGROUND_LOCATION (Android 10+)
2. **Interval**: Option to revert to minutes (or keep seconds with min/max validation)
3. **Battery optimization**: Test on different devices with Doze mode
4. ~~**Icons**: Add custom launcher icons~~ ✅ Done - Custom red icon with rounded "L"
5. **Notification permission**: Already implemented - requests on first launch (Android 13+)

### Known Limitations
1. WorkManager PeriodicWork minimum = 15 minutes
2. No notification during checks (by design)
3. GPS accuracy depends on device power-saving settings
4. Android Doze mode may pause background checks when device is idle (normal battery optimization)

### Doze Mode Considerations

**Current Implementation:** WorkManager (battery-friendly)

**Doze Impact with 2-minute intervals:**
- ✅ **In motion** (typical transport use): Doze doesn't activate, works perfectly
- ⚠️ **Stationary 30+ minutes** with screen off: Checks may be delayed/batched
- 🔋 **Battery friendly**: Respects Android power management

**If Doze becomes problematic in real-world usage:**

**Option 1: Foreground Service** (not recommended)
- Pros: Never interrupted, guaranteed precision
- Cons: Mandatory permanent notification, higher battery drain
- Use case: Only if critical precision needed

**Option 2: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** (recommended if needed)
- Pros: Exempts app from Doze, no permanent notification
- Cons: User must manually approve, Google Play may scrutinize
- Implementation:
  ```kotlin
  // In AndroidManifest.xml
  <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
  
  // Request exemption
  val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
  intent.data = Uri.parse("package:$packageName")
  startActivity(intent)
  ```
- Add in Settings UI: "Disable battery optimization" option with explanation

**Decision:** Stay with current WorkManager implementation. Only implement Option 2 if real-world testing reveals issues.

## Tests Performed

### Functional Tests ✅
- [x] Create/delete points on map
- [x] Edit marker properties (name, time window with HH:MM precision)
- [x] View marker details via info card
- [x] Scroll pickers for time selection (hours and minutes)
- [x] Automatic centering on user position
- [x] Enable/disable tracking
- [x] Modify interval and distance
- [x] Worker runs at short intervals (30-60s)
- [x] Proximity detection with time window validation
- [x] Fairtiq launch (with notification + full-screen intent)
- [x] Phone vibration (direct vibration, works with screen off)
- [x] Anti-spam (1 trigger per zone entry)
- [x] Persistence after app closure
- [x] Detailed logs in LocationCheckWorker
- [x] Red proximity circles on map
- [x] Auto-start on boot (with GPS cold start handling)
- [x] GPS location after reboot without manual intervention
- [x] Notification permission request on first launch (Android 13+)
- [x] Vibration and app launch with screen locked
- [x] Map layer selection (Street, Topographic) with floating button
- [x] Full-screen immersive UI with floating action buttons
- [x] GPS centering button to quickly return to current location
- [x] Weekday selection in settings (choose active monitoring days)
- [x] Location search with Photon geocoding API
- [x] Search result markers with distinct red pin appearance

### Technical Tests ✅
- [x] Gradle build
- [x] Hilt injection
- [x] Room migrations (none needed yet)
- [x] DataStore persistence
- [x] WorkManager scheduling
- [x] OneTimeWork rescheduling

## Typical Logs

### Boot Sequence
```
BootReceiver: Device booted, checking if tracking was enabled
BootReceiver: Settings file exists, scheduling worker to check settings
BootReceiver: Worker scheduled successfully with 30s delay
LocationCheckWorker: Starting location check...
LocationCheckWorker: Settings: interval=5s, distance=100m, enabled=true
LocationCheckWorker: Requesting fresh location...
LocationCheckWorker: Current location: 43.342346, 1.5203021
```

### Worker Success
```
LocationCheckWorker: Starting location check...
LocationCheckWorker: Settings: interval=30s, distance=200m, enabled=true
LocationCheckWorker: Current location: 43.3423324, 1.5203386
LocationCheckWorker: No proximity zones entered
LocationCheckWorker: Rescheduling next check in 30s
```

### Proximity Triggered
```
LocationCheckWorker: Entered proximity zone for 1 point(s)
LocationCheckWorker: Launched Fairtiq app
LocationCheckWorker: Vibration triggered
```

### Worker Error
```
LocationCheckWorker: Location permission not granted
LocationCheckWorker: Error during location check
```

## Git

### First Commit
Commit made with all main features.

### Important Files
- `README.md` - User documentation
- `AGENTS.md` - Guidelines for AI agents
- `TODO.md` - Project status + roadmap
- `DEVELOPMENT.md` - This file (technical notes)

## Contact / Support
Personal project - Sylvain
