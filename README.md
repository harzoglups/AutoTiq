# FairLaunch

Android application to automate Fairtiq app launch based on geolocation.

## Features

- **Interactive OpenStreetMap**: Full-screen immersive map with zoom, pan, rotation
- **Modern UI**: Edge-to-edge design with floating action buttons (Google Maps style)
- **Map Layer Selection**: Switch between Street and Topographic views via floating button (top-right)
- **Points of Interest Management**:
  - Long press on map to create a point
  - Short tap on marker to view details (name, active time window)
  - Long press on marker to delete it
  - Editable properties: name, active time window (hour:minute precision with scroll pickers)
  - Visual proximity circles (red semi-transparent) around each point
  - Persistent local storage of points (Room)
- **Background Tracking**:
  - Periodic GPS position checking
  - Configurable proximity detection
  - Time-based activation: points only trigger within their configured time window
  - Automatic launch of Fairtiq application
  - Phone vibration on trigger
  - Auto-start on device boot
- **Configurable Settings**:
  - Position check frequency (seconds or minutes)
  - Proximity distance (meters)
  - Enable/disable tracking
- **Anti-spam**: Only triggers once per zone entry (requires leaving and returning)

## Architecture

Clean Architecture with 3 layers:

### Domain Layer (Pure Kotlin)
- `model/` - Entities: MapPoint, AppSettings, ProximityState
- `repository/` - Repository interfaces
- `usecase/` - Business logic: Add/Delete points, Check proximity, Settings management

### Data Layer
- `local/dao/` - Room DAOs (MapPointDao, ProximityStateDao)
- `local/entity/` - Room entities
- `repository/` - Repository implementations
- `mapper/` - Entity ↔ Domain mappers

### App Layer (Presentation)
- `ui/map/` - Map screen with OpenStreetMap
- `ui/settings/` - Settings screen
- `worker/` - WorkManager for background tracking
- `di/` - Hilt modules

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: Clean Architecture (Domain/Data/App)
- **Map**: OpenStreetMap (osmdroid)
- **Location**: Google Play Services Location
- **Background Tasks**: WorkManager
- **DI**: Hilt
- **Async**: Coroutines + Flow
- **Database**: Room
- **Preferences**: DataStore
- **Icon**: Custom vector drawable (red "L" on adaptive icon)

## Installation and Setup

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 24 minimum (Android 7.0)
- Android SDK 34 for compilation

### Build

```bash
# Clone the repository
cd FairLaunch

# Build the project
./gradlew build

# Assemble debug APK
./gradlew assembleDebug

# Run tests
./gradlew test
```

## Usage

1. **First Use**:
   - The app requests location permissions - accept to use the app
   - The app requests notification permissions (Android 13+) - **required for alerts**
   - Accept BACKGROUND_LOCATION in settings to enable background tracking

2. **Create Points**:
   - Long press (500ms) on the map to create a point
   - Points appear as markers with proximity circles
   - Edit dialog opens automatically: set name and active time window (HH:MM precision)

3. **View/Edit Points**:
   - Short tap on a marker to view its details card
   - Tap "Edit" in the card to modify name or time window
   - Use scroll pickers to select hours and minutes precisely

4. **Delete Points**:
   - Long press (500ms) on a marker to delete it
   - Markers have a large touch zone for easy interaction

5. **Configure Tracking**:
   - Tap the floating settings button (bottom-right)
   - Configure check frequency (default: 300 seconds = 5 minutes)
   - Configure proximity distance (default: 200 meters)
   - Enable tracking via the switch

6. **Change Map View**:
   - Tap the floating layers button (top-right corner of the map)
   - Select desired map type: Street or Topographic
   - Selection is saved automatically

7. **Automatic Operation**:
   - The app checks your position in the background
   - Red circles on the map show proximity zones
   - When you enter a zone (at configured distance) **within the point's active time window**:
     - The phone vibrates (strong 3-burst pattern)
     - The screen wakes up if locked
     - The Fairtiq app launches automatically
   - You must leave and re-enter the zone to trigger again
   - **Tracking persists after device reboot** (auto-restart if enabled)

## Permissions

- `ACCESS_FINE_LOCATION`: Precise location
- `ACCESS_COARSE_LOCATION`: Approximate location (fallback)
- `ACCESS_BACKGROUND_LOCATION`: Background location
- `RECEIVE_BOOT_COMPLETED`: Auto-start tracking on device boot
- `POST_NOTIFICATIONS`: Notification alerts (Android 13+)
- `USE_FULL_SCREEN_INTENT`: Wake screen when entering zone
- `VIBRATE`: Phone vibration
- `INTERNET`: Map tiles loading

## Project Structure

```
FairLaunch/
├── app/                    # UI layer
│   └── src/main/java/com/fairlaunch/
│       ├── di/            # Dependency injection
│       ├── receiver/      # Boot receiver for auto-start
│       ├── worker/        # Background location worker (WorkManager)
│       ├── ui/
│       │   ├── map/       # Map screen with proximity circles
│       │   ├── settings/  # Settings screen
│       │   └── theme/     # Compose theme
│       └── MainActivity.kt
├── data/                   # Data layer
│   └── src/main/java/com/fairlaunch/data/
│       ├── local/         # Room database
│       ├── repository/    # Repository implementations
│       └── mapper/        # Mappers
└── domain/                 # Domain layer (pure Kotlin)
    └── src/main/java/com/fairlaunch/domain/
        ├── model/         # Domain entities
        ├── repository/    # Repository interfaces
        └── usecase/       # Business logic
```

## Development

See [AGENTS.md](AGENTS.md) for detailed development guidelines.

## Releases

This project uses automated releases with semantic versioning based on conventional commits.

### Creating a Release (Method 1: GitHub UI)

1. Go to GitHub Actions tab
2. Select "Create Release" workflow
3. Click "Run workflow"
4. Choose version bump type (major/minor/patch)
5. The workflow will:
   - Calculate the next version automatically
   - Generate release notes from commits
   - Build and attach the APK
   - Create a GitHub release with the tag

### Creating a Release (Method 2: Local Script)

```bash
# Make sure you're on main branch with a clean working directory
git checkout main
git pull origin main

# Create a release tag (major/minor/patch)
./create-release.sh minor

# Push the tag to trigger the release
git push origin v1.0.0  # Replace with your tag
```

The tag push will automatically trigger the GitHub Actions workflow to build and create the release.

### Conventional Commit Format

Use these prefixes for commits to get organized release notes:

- `feat:` - New features (appears under ✨ Features)
- `fix:` - Bug fixes (appears under 🐛 Bug Fixes)
- `docs:` - Documentation (appears under 📚 Documentation)
- `refactor:` - Code refactoring (appears under ♻️ Refactoring)
- `chore:` - Maintenance tasks (appears under 🔧 Chores)

Example:
```bash
git commit -m "feat(map): add new marker clustering feature"
git commit -m "fix(location): resolve background tracking issue"
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
