# AutoTiq Scripts

This directory contains all automation scripts for the AutoTiq project, organized by usage category.

## Directory Structure

```
scripts/
├── build/          # Build and release scripts
├── deploy/         # Deployment scripts
├── dev/            # Development and debugging tools
└── docs/           # Documentation generation
```

## Build Scripts (`build/`)

Scripts for building releases and managing versions.

### `build-play-store.sh`
Builds the release AAB (Android App Bundle) for Play Store submission.

```bash
./scripts/build/build-play-store.sh
```

**What it does:**
- Builds optimized release AAB with ProGuard
- Signs with debug keystore
- Outputs to `app/build/outputs/bundle/release/`

### `create-release.sh`
Creates a new release with version bump, git tag, and Play Store build.

```bash
./scripts/build/create-release.sh [patch|minor|major]
```

**What it does:**
- Bumps version in `build.gradle.kts`
- Commits version change
- Creates git tag
- Builds release AAB
- Generates release notes

**Examples:**
```bash
./scripts/build/create-release.sh patch  # 1.0.0 -> 1.0.1
./scripts/build/create-release.sh minor  # 1.0.0 -> 1.1.0
./scripts/build/create-release.sh major  # 1.0.0 -> 2.0.0
```

### `update-version.sh`
Updates version numbers in `build.gradle.kts`.

```bash
./scripts/build/update-version.sh <new_version>
```

**Example:**
```bash
./scripts/build/update-version.sh 1.2.0
```

---

## Deployment Scripts (`deploy/`)

Scripts for deploying resources to external services.

### `deploy-privacy-policy.sh`
Deploys privacy policy to VPS.

```bash
./scripts/deploy/deploy-privacy-policy.sh
```

**Setup** (first time only):
```bash
# Copy .env.example to .env and customize with your VPS settings
cp .env.example .env
# Edit .env with your VPS_USER (SSH username)
```

**What it does:**
- Loads VPS configuration from `.env` file
- Extracts version from `build.gradle.kts`
- Updates date to current date in HTML
- Updates version number in HTML
- Deploys to VPS via scp
- Verifies HTTPS access

**When to run:**
- ✅ Before submitting new version to Play Console
- ✅ When privacy policy content changes
- ✅ When app version changes

---

## Development Scripts (`dev/`)

Tools for development, debugging, and testing.

### `memory-snapshot.sh`
Captures memory snapshot and analyzes for leaks.

```bash
./scripts/dev/memory-snapshot.sh
```

**What it does:**
- Triggers GC on device
- Captures heap dump
- Converts to HPROF format
- Analyzes with MAT (if installed)

### `monitor-memory.sh`
Real-time memory monitoring during app usage.

```bash
./scripts/dev/monitor-memory.sh [interval_seconds]
```

**Example:**
```bash
./scripts/dev/monitor-memory.sh 5  # Monitor every 5 seconds
```

### `test-memory-optimizations.sh`
Tests memory usage across different map scenarios.

```bash
./scripts/dev/test-memory-optimizations.sh
```

**What it tests:**
- Baseline memory
- After loading map
- After adding markers
- After switching layers
- After navigation
- Memory cleanup

### `test-release-notes.sh`
Tests release notes generation for Play Store.

```bash
./scripts/dev/test-release-notes.sh
```

---

## Documentation Scripts (`docs/`)

Scripts for generating documentation and diagrams.

### `generate-diagrams.sh`
Generates PlantUML diagrams from `.puml` files in `docs/`.

```bash
./scripts/docs/generate-diagrams.sh
```

**Requirements:**
- PlantUML installed
- GraphViz (optional, for better rendering)

**What it generates:**
- Architecture diagrams
- Data layer diagrams
- Domain model diagrams
- Sequence diagrams
- UI layer diagrams

**Output:** PNG files in `docs/` directory

---

## Common Workflows

### Creating a New Release
```bash
# 1. Update version and create tag
./scripts/build/create-release.sh minor

# 2. Deploy privacy policy (if needed)
./scripts/deploy/deploy-privacy-policy.sh

# 3. Build AAB is already created by create-release.sh
# Upload app/build/outputs/bundle/release/app-release.aab to Play Console
```

### Before Play Store Submission
```bash
# 1. Build release AAB
./scripts/build/build-play-store.sh

# 2. Deploy privacy policy
./scripts/deploy/deploy-privacy-policy.sh

# 3. Upload AAB to Play Console
```

### Memory Optimization Workflow
```bash
# 1. Establish baseline
./scripts/dev/memory-snapshot.sh

# 2. Monitor during testing
./scripts/dev/monitor-memory.sh 5

# 3. Run full memory test suite
./scripts/dev/test-memory-optimizations.sh
```

---

## Notes

- All scripts assume they are run from the **project root** directory
- Scripts use relative paths from project root
- Make sure scripts are executable: `chmod +x scripts/**/*.sh`
- Most scripts require an Android device connected via ADB
- VPS deployment scripts require SSH access (configure in `.env` file)

---

## Troubleshooting

### "Permission denied" error
```bash
chmod +x scripts/**/*.sh
```

### "Java not found" error
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### "Device not found" error
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

### "SSH connection failed"
Check VPS access (replace with your settings from `.env`):
```bash
ssh $VPS_USER@$VPS_HOST "echo 'Connected'"
```
