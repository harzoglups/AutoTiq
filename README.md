# AutoTiq

**Never miss your Fairtiq check-in again!**

AutoTiq is an Android application that automatically launches the Fairtiq app when you approach your saved transit points. It wakes your phone, vibrates, and displays Fairtiq on your lock screen - making it impossible to forget to start your journey before boarding the train.

## 🎯 Why AutoTiq?

Forgetting to check-in with Fairtiq before boarding public transport can result in a fine for an honest mistake. AutoTiq solves this problem by:

1. **Detecting when you approach** your saved train/bus stations
2. **Waking your phone screen** even if locked
3. **Vibrating to get your attention** (configurable, default: 3 bursts)
4. **Automatically opening Fairtiq** so it's the first thing you see
5. **No manual intervention needed** - it works completely in the background

This way, you'll never forget to start your journey in Fairtiq, avoiding unnecessary fines for simple oversights.

## ✨ Features

- **Animated Splash Screen**: Welcome screen with rotating logo on pastel background
- **Guided Onboarding**: Step-by-step permission setup on first launch (multilingual)
- **Interactive OpenStreetMap**: Full-screen immersive map with zoom, pan, rotation
- **Modern UI**: Edge-to-edge design with floating action buttons (Google Maps style)
- **Location Search**: Search for cities, addresses, and places with autocomplete (Photon geocoding)
- **GPS Centering**: Quick button to center map on current location
- **Map Layer Selection**: Switch between Street and Topographic views via floating button (top-right)
- **Points of Interest Management**:
  - Long press on map to create a point
  - Short tap on marker to view details (name, active time window)
  - Long press on marker to delete it
  - Editable properties: name, active time window (hour:minute precision with scroll pickers)
  - Visual proximity circles (red semi-transparent) around each point
  - Persistent local storage of points (Room)
- **Background Tracking**:
  - Geofencing (Google Play Services) as primary detection: event-driven, low battery, reacts to both zone entry and exit
  - Periodic GPS position checking as a backup (WorkManager)
  - Configurable proximity detection
  - Time-based activation: points only trigger within their configured time window
  - Automatic launch of Fairtiq application
  - Configurable vibration on trigger (default: 3 bursts)
  - Screen wake-up even when locked
  - Auto-start on device boot, and automatic restore after an app update
- **Event Log** (Settings → Event log): on-device journal of the last 200 background events
  (zone entries/exits, alerts sent or skipped and why, worker runs, restores, errors) with a share button,
  to understand why an alert was missed without needing adb
- **Configurable Settings**:
  - Position check frequency (seconds or minutes)
  - Proximity distance (meters)
  - Vibration count (number of vibration bursts)
  - Enable/disable tracking
  - Weekday selection: choose which days GPS monitoring is active (battery saving)
- **Anti-spam**: Only triggers once per zone entry (requires leaving and returning; the exit is detected by the geofence EXIT transition)
- **Multilingual**: English, French, German, Spanish, Italian, Portuguese

## 📱 Installation

### Method 1: Install Pre-built APK (Recommended)

1. **Download the APK**:
   - Go to the [Releases page](https://github.com/yourusername/AutoTiq/releases)
   - Download the latest `AutoTiq-vX.X.X.apk` file

2. **Enable installation from unknown sources**:
   - Open **Settings** on your Android device
   - Go to **Security** (or **Apps & notifications** → **Advanced** → **Special app access**)
   - Enable **Install unknown apps** for your browser or file manager

3. **Install the APK**:
   - Open the downloaded APK file from your notifications or file manager
   - Tap **Install**
   - Wait for the installation to complete

4. **Grant permissions during onboarding**:
   - Open AutoTiq
   - Follow the onboarding wizard
   - **IMPORTANT**: When asked for location permission, select **"Allow all the time"** (not "Only while using the app")
   - Grant notification permission (Android 13+)

### Method 2: Build from Source

#### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 24 minimum (Android 7.0)
- Android SDK 34 for compilation
- JDK 17

#### Build Steps

```bash
# Clone the repository
git clone https://github.com/yourusername/AutoTiq.git
cd AutoTiq

# Build the project
./gradlew build

# Assemble debug APK
./gradlew assembleDebug

# The APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

#### Install via ADB

```bash
# Connect your Android device via USB with USB debugging enabled
# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.autotiq/.MainActivity
```

## 🚀 Usage

### Configuration (First Time Setup)

For VPS deployment of privacy policy, create a `.env` file:

```bash
cp .env.example .env
# Edit .env with your VPS_USER (SSH username for deployment)
```

**Note**: If you fork this project, update the VPS host/paths in `scripts/deploy/deploy-privacy-policy.sh` and email addresses in `docs/` HTML files.

### First Launch

1. **Splash Screen**: Welcome animation (3 seconds)
2. **Onboarding Wizard**:
   - **Welcome screen**: Introduction to AutoTiq
   - **Location permission**: Allow access to your location
   - **Background location permission**: **CRITICAL** - Select "Allow all the time"
   - **Notifications** (Android 13+): Allow notifications for alerts
   - **Completion**: Ready to use!

### Search for Locations

- Use the search bar at the top of the map
- Type at least 3 characters (e.g., "Paris", "train station", "address")
- Select a result from the autocomplete dropdown
- A red pin marker shows the searched location
- Tap anywhere on the map to remove the search marker

### Navigate the Map

- Tap the GPS button (bottom-right) to center on your current location
- Pinch to zoom, drag to pan, rotate with two fingers
- Tap the layers button (top-right) to switch between Street/Topographic views

### Create Points

- Long press (500ms) on the map to create a point
- Points appear as markers with proximity circles
- Edit dialog opens automatically: set name and active time window (HH:MM precision)

### View/Edit Points

- Short tap on a marker to view its details card
- Tap "Edit" in the card to modify name or time window
- Use scroll pickers to select hours and minutes precisely

### Delete Points

- Long press (500ms) on a marker to delete it
- Markers have a large touch zone for easy interaction

### Configure Tracking

- Tap the floating settings button (bottom-right)
- Configure check frequency (default: 300 seconds = 5 minutes)
- Configure proximity distance (default: 200 meters)
- Configure vibration count (default: 3 bursts)
- Select active weekdays (uncheck days when you don't take the train)
- Enable tracking via the switch

### Change Map View

- Tap the floating layers button (top-right corner of the map)
- Select desired map type: Street or Topographic
- Selection is saved automatically

### Automatic Operation

- The app checks your position in the background
- Red circles on the map show proximity zones
- When you enter a zone (at configured distance) **within the point's active time window**:
  - The phone vibrates (configurable number of bursts)
  - The screen wakes up if locked
  - The Fairtiq app launches automatically
  - **You see Fairtiq immediately** - no risk of forgetting to check-in!
- You must leave and re-enter the zone to trigger again
- **Tracking persists after device reboot** (auto-restart if enabled)

### Keeping it reliable over time

For detection to keep working without ever opening the app, check these Android settings
(Settings → Apps → AutoTiq):

- **Location**: "Allow all the time"
- **Battery**: "Unrestricted" / not optimized (the app offers a shortcut)
- **Unused app settings**: turn **off** "Pause app activity if unused" / "Manage app if unused".
  When it is on, Android force-stops apps that are not opened for weeks, cancels their geofences and
  scheduled work, and can revoke their permissions.

If an alert is ever missed, open **Settings → Event log** and use the share button: it shows whether the
geofence fired, and if not alerting, the reason.

## 🔒 Permissions

The app requires the following permissions, explained during onboarding:

- `ACCESS_FINE_LOCATION`: Precise location to detect proximity to your points
- `ACCESS_COARSE_LOCATION`: Approximate location (fallback)
- `ACCESS_BACKGROUND_LOCATION`: **CRITICAL** - Required for automatic detection when app is closed
- `RECEIVE_BOOT_COMPLETED`: Auto-start tracking on device boot
- `POST_NOTIFICATIONS`: Notification alerts (Android 13+)
- `USE_FULL_SCREEN_INTENT`: Wake screen when entering zone
- `VIBRATE`: Phone vibration to alert you
- `INTERNET`: Map tiles loading and location search

## 🏗️ Architecture

AutoTiq follows Clean Architecture principles with 3 distinct layers:

### Architecture Diagrams

**Architecture Overview**

![Architecture Overview](docs/AutoTiq-Architecture-Overview.png)

<details>
<summary><b>Domain Model</b> (Click to expand)</summary>

![Domain Model](docs/AutoTiq-Domain-Model.png)

</details>

<details>
<summary><b>Data Layer</b> (Click to expand)</summary>

![Data Layer](docs/AutoTiq-Data-Layer.png)

</details>

<details>
<summary><b>UI Layer</b> (Click to expand)</summary>

![UI Layer](docs/AutoTiq-UI-Layer.png)

</details>

<details>
<summary><b>Proximity Check Sequence</b> (Click to expand)</summary>

![Proximity Check Sequence](docs/AutoTiq-Sequence-Proximity-Check.png)

</details>

<details>
<summary><b>User Journey</b> (Click to expand)</summary>

![User Journey](docs/AutoTiq-Sequence-User-Journey.png)

</details>

> 💡 **Note**: PlantUML source files are available in the [`docs/`](docs/) folder. Use `./scripts/docs/generate-diagrams.sh` to regenerate PNG images after modifying `.puml` files.

### Domain Layer (Pure Kotlin)
- `model/` - Entities: MapPoint, AppSettings, ProximityState
- `repository/` - Repository interfaces
- `usecase/` - Business logic: Add/Delete points, Check proximity, Settings management

### Data Layer
- `local/dao/` - Room DAOs (MapPointDao, ProximityStateDao, EventLogDao)
- `local/SettingsDataStore.kt` - The single shared settings DataStore (never declare another one for the "settings" file)
- `local/entity/` - Room entities
- `repository/` - Repository implementations
- `mapper/` - Entity ↔ Domain mappers

### App Layer (Presentation)
- `ui/splash/` - Splash screen with animation
- `ui/onboarding/` - Guided permission setup
- `ui/map/` - Map screen with OpenStreetMap
- `ui/settings/` - Settings screen
- `ui/eventlog/` - Event log screen
- `geofence/` - Geofence registration, transition receiver (ENTER/EXIT) and alert helper
- `receiver/` - Boot / app update receiver that restores geofences and the worker
- `worker/` - WorkManager for backup background tracking
- `di/` - Hilt modules

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: Clean Architecture (Domain/Data/App)
- **Map**: OpenStreetMap (osmdroid)
- **Geocoding**: Photon API (Komoot) for location search
- **Location**: Google Play Services Location
- **Background Tasks**: Geofencing API (primary) + WorkManager (backup)
- **DI**: Hilt
- **Async**: Coroutines + Flow
- **Database**: Room
- **Preferences**: DataStore (settings, single shared instance) + SharedPreferences (onboarding flag)
- **Icon**: Custom vector drawable (white "L" on adaptive icon)

## 📂 Project Structure

```
AutoTiq/
├── app/                    # UI layer
│   └── src/main/java/com/autotiq/
│       ├── di/            # Dependency injection
│       ├── receiver/      # Boot / app update receiver (restores geofences and worker)
│       ├── geofence/      # Geofence manager, transition receiver, alert helper
│       ├── worker/        # Backup location worker (WorkManager)
│       ├── ui/
│       │   ├── splash/    # Splash screen with animation
│       │   ├── onboarding/ # Guided permission setup
│       │   ├── map/       # Map screen with proximity circles
│       │   ├── settings/  # Settings screen
│       │   ├── eventlog/  # Event log screen
│       │   ├── navigation/ # Navigation graph
│       │   └── theme/     # Compose theme
│       └── MainActivity.kt
├── data/                   # Data layer
│   └── src/main/java/com/autotiq/data/
│       ├── local/         # Room database
│       ├── repository/    # Repository implementations
│       └── mapper/        # Mappers
└── domain/                 # Domain layer (pure Kotlin)
    └── src/main/java/com/autotiq/domain/
        ├── model/         # Domain entities
        ├── repository/    # Repository interfaces
        └── usecase/       # Business logic
```

## 🔧 Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed development documentation.

See [AGENTS.md](AGENTS.md) for AI agent development guidelines.

## 🚀 Releases

This project uses **fully automated semantic versioning** based on conventional commits.

### How It Works 🤖

1. **Write commits** using conventional commit format
2. **When ready for a release**:
   - **Option A**: Go to Actions → "Auto Release" → Click "Run workflow" 
   - **Option B**: Push a tag: `git push origin v1.0.0`
3. The workflow automatically:
   - Analyzes all commits since last release
   - Determines the correct version bump (major/minor/patch)
   - Builds the APK
   - Creates the GitHub release

**You control WHEN to release, the workflow decides WHAT version.**

### Creating a Release

#### Method 1: Manual Trigger (Recommended)

1. Go to **Actions** → **Auto Release**
2. Click **"Run workflow"**
3. Select branch: `main`
4. Click **"Run workflow"**

The workflow analyzes your commits and creates the appropriate version automatically!

#### Method 2: Push a Tag

```bash
# The tag value doesn't matter - it will be ignored
git tag v0.0.0
git push origin v0.0.0
```

The "Build and Release on Tag" workflow will trigger and create the correct version.

### Version Bump Rules

The version is determined by analyzing your commit messages:

| Commit Type | Version Bump | Example |
|-------------|--------------|---------|
| `feat:` | **MINOR** (0.X.0) | `feat(map): add marker clustering` |
| `fix:` | **PATCH** (0.0.X) | `fix(location): resolve GPS issue` |
| `feat!:` or `BREAKING CHANGE:` | **MAJOR** (X.0.0) | `feat!: redesign entire API` |
| `docs:`, `refactor:`, `chore:` | **PATCH** (0.0.X) | `docs: update README` |

**Priority**: MAJOR > MINOR > PATCH. If you have both `feat:` and `fix:` commits, you'll get a MINOR bump.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Fairtiq for their excellent public transport ticketing app
- OpenStreetMap contributors for map data
- Komoot for the Photon geocoding API
