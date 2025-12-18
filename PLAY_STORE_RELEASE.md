# Play Store Release Plan

## Overview

This document describes the process for building and releasing AutoTiq on Google Play Store, maintaining version coherence with GitHub releases.

## Strategy: Local AAB Build with Automatic Versioning

### Key Principles

1. **Version Source of Truth**: `app/build.gradle.kts` contains the official Play Store version
2. **Automatic Version Calculation**: Version is calculated from git history using conventional commits (same logic as GitHub workflow)
3. **Full Alignment**: Git tags, build.gradle.kts, and Play Store versions all match
4. **Traceability**: Version bumps are committed to git before building AAB

### Version Numbering

- **versionName**: Semantic versioning (e.g., "1.6.0") - visible to users
- **versionCode**: Integer incremented with each release - internal Android versioning

---

## Current Status

- **Current git tag**: `v1.5.0`
- **Current build.gradle.kts**: `versionCode = 1`, `versionName = "1.0.0"` (out of sync)
- **Next calculated version**: `v1.6.0` (based on commits since v1.5.0 containing features)

---

## Build Script: `build-play-store.sh`

### What It Does

The script automates the entire AAB build process:

1. **Fetch latest git tag** (e.g., `v1.5.0`)
2. **Analyze commits** since last tag using conventional commit format
3. **Detect bump type**:
   - `feat:` commits → **MINOR** bump (1.5.0 → 1.6.0)
   - `fix:`, `chore:`, `refactor:` commits → **PATCH** bump (1.5.0 → 1.5.1)
   - `BREAKING CHANGE` or `!:` → **MAJOR** bump (1.5.0 → 2.0.0)
4. **Update build.gradle.kts**:
   - Set `versionName` to calculated version
   - Increment `versionCode` automatically
5. **Commit version changes** to git: `"chore: bump version to X.Y.Z for Play Store release"`
6. **Build signed AAB**: `./gradlew bundleRelease`
7. **Copy AAB** with versioned filename: `AutoTiq-vX.Y.Z.aab`
8. **Display summary** with next steps

### Usage

```bash
# Standard build (auto-detects version from git)
./scripts/build/build-play-store.sh

# Dry-run mode (shows what would happen without building)
./scripts/build/build-play-store.sh --dry-run

# Force specific version (overrides auto-detection)
./scripts/build/build-play-store.sh --version 1.0.0
```

---

## Keystore Configuration (To Be Completed)

### Current State

- ❌ No production keystore created yet
- ❌ No `keystore.properties` file
- ⚠️ Using debug keystore for testing builds

### Required Steps (Before First Play Store Release)

#### Option A: Google Play App Signing (Recommended)

**Advantages:**
- ✅ Google manages production key (automatic backups, key rotation)
- ✅ Upload key is less critical (can be reset if lost)
- ✅ Industry standard, recommended by Google
- ✅ Simpler disaster recovery

**Setup:**
1. Create upload keystore:
   ```bash
   keytool -genkey -v -keystore ~/.android/autotiq-upload-key.jks \
     -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` (already in .gitignore):
   ```properties
   storeFile=/Users/sylvain/.android/autotiq-upload-key.jks
   storePassword=***
   keyAlias=upload
   keyPassword=***
   ```

3. Update `app/build.gradle.kts` to load keystore properties

4. During first Play Store upload, enable "Play App Signing"

#### Option B: Self-Managed Keystore

**Advantages:**
- Full control over production key
- No dependency on Google key management

**Disadvantages:**
- You are responsible for secure backups
- If lost, cannot update app anymore (must publish new app)

**Setup:** Same as Option A but without Play App Signing

---

## Gradle Configuration (To Be Completed)

### Changes Needed in `app/build.gradle.kts`

```kotlin
// Load keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    // ... existing config ...

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] ?: "")
            storePassword = keystoreProperties["storePassword"] as String?
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug") // Fallback for testing
            }
            // ... existing release config ...
        }
    }

    // Enable AAB optimizations
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}
```

---

## Workflow: Future Releases

### 1. Development Phase

Normal git workflow:
```bash
git add .
git commit -m "feat: add new feature"
git push origin main
```

### 2. Prepare Play Store Release

When ready to publish:

```bash
# Build AAB (auto-updates version, commits changes)
./scripts/build/build-play-store.sh

# Review changes
git log -1
git show HEAD

# Push version bump commit
git push origin main
```

### 3. Upload to Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Navigate to app → Release → Production
3. Create new release
4. Upload `AutoTiq-vX.Y.Z.aab`
5. Fill release notes (can use generated content from script)
6. Submit for review

### 4. Create Git Tag (Optional but Recommended)

```bash
# Tag the version
git tag vX.Y.Z

# Push tag
git push origin vX.Y.Z
```

This triggers GitHub workflow to create a GitHub Release with APK.

---

## Version Coherence Strategy

### Play Store (AAB)
- Built locally with `./scripts/build/build-play-store.sh`
- Version calculated from git commits
- Signed with production keystore
- Uploaded manually to Play Console

### GitHub Releases (APK)
- Built automatically by GitHub Actions workflow
- Triggered by git tags or manual dispatch
- Signed with debug keystore (for advanced users)
- Versions can be independent but should align

### Source Code (build.gradle.kts)
- Always reflects the last Play Store release version
- Updated by `build-play-store.sh` script
- Committed before building AAB
- Single source of truth

---

## Initial Release Decision

### Version Number for First Play Store Release

**Current situation:**
- Git history has tags up to `v1.5.0`
- build.gradle.kts shows `1.0.0` (out of sync)
- Commits since v1.5.0 contain features → would calculate v1.6.0

**Options:**

#### Option A: Start at 1.0.0 (Fresh Start)
- Ignore git history
- Start Play Store versioning from scratch
- Force `--version 1.0.0` in first build

**Pros:**
- "Official" first public release feeling
- Clean slate for Play Store users

**Cons:**
- Disconnect between git tags and Play Store versions
- Confusion for developers looking at git history

#### Option B: Start at 1.6.0 (Git History Alignment)
- Use automatic version calculation
- Continue from existing git versioning
- Full coherence between git and Play Store

**Pros:**
- Perfect alignment: git tags = Play Store versions
- No confusion for developers
- Traceability throughout entire project history

**Cons:**
- Play Store users see "1.6.0" as first version (cosmetic only)

**Recommendation:** **Option B (1.6.0)** for full coherence and traceability.

---

## Security Considerations

### Keystore Management

**Critical files to protect:**
- `~/.android/autotiq-upload-key.jks` - The upload keystore
- `keystore.properties` - Contains passwords (already in .gitignore)

**Best practices:**
- ✅ Store passwords in password manager (1Password, Bitwarden, etc.)
- ✅ Backup keystore to encrypted cloud storage (Google Drive, Dropbox, etc.)
- ✅ Keep offline copy on USB drive in secure location
- ✅ **NEVER commit keystore or passwords to git**
- ✅ Document password recovery process

### Git Ignore Configuration

Already configured in `.gitignore`:
```
# Keystore files
*.jks
*.keystore

# Keystore properties
keystore.properties
```

---

## Play Store Listing Information

All content ready in `PLAY_STORE_DESCRIPTION.md`:

- ✅ Short description (80 chars) in 7 languages
- ✅ Full description in 7 languages (FR, EN, DE, IT, CS, SV, NO)
- ✅ Legal disclaimers (Fairtiq trademark, OpenStreetMap attribution)
- ✅ Feature list and screenshots guidance

**Languages supported:**
1. French (primary)
2. English
3. German
4. Italian
5. Czech
6. Swedish
7. Norwegian

---

## Testing Before Release

### Pre-Release Checklist

Before uploading to Play Console:

- [ ] Test AAB installation on clean device
- [ ] Verify version number in app (Settings → Info)
- [ ] Test all core features:
  - [ ] Map display and marker creation
  - [ ] Proximity detection and notifications
  - [ ] Background location tracking
  - [ ] Fairtiq app launch
  - [ ] Time windows and day selection
  - [ ] Multi-language support
- [ ] Test on Android 8.0 (minSdk 26)
- [ ] Test on Android 15 (targetSdk 36)
- [ ] Verify ProGuard doesn't break anything
- [ ] Check app size (AAB should be ~15-20 MB)

### Internal Testing Track

Google Play Console offers internal testing:

1. Upload AAB to "Internal testing" track first
2. Add yourself as tester
3. Install from Play Store
4. Verify everything works
5. Promote to "Production" when ready

---

## Troubleshooting

### "Version code already exists"
- Play Store rejects AAB if versionCode already used
- Solution: `build-play-store.sh` auto-increments versionCode
- Manual fix: Edit build.gradle.kts and increment versionCode

### "Signature doesn't match"
- Using wrong keystore
- Solution: Ensure `keystore.properties` points to correct keystore
- For first upload: Enable Google Play App Signing

### "Build failed"
- Check keystore.properties exists and is valid
- Verify keystore password is correct
- Run `./gradlew clean` and retry

### "No commits since last tag"
- Script won't build if no changes
- Solution: Make changes and commit, or use `--force` flag

---

## Future Enhancements

### Possible Improvements

1. **Automated Play Store Upload**
   - Use Gradle Play Publisher plugin
   - Store credentials in GitHub Secrets
   - Fully automated release pipeline

2. **Release Notes Generation**
   - Auto-generate release notes from commits
   - Multi-language support
   - Direct upload to Play Console

3. **Beta Testing Integration**
   - Automatic promotion: internal → beta → production
   - Gradual rollout configuration
   - A/B testing setup

4. **Crash Reporting**
   - Firebase Crashlytics integration
   - Monitor production crashes
   - Automatic bug reporting

---

## References

- [Google Play Console](https://play.google.com/console)
- [Android App Bundle Documentation](https://developer.android.com/guide/app-bundle)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Semantic Versioning](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
