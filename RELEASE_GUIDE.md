# Release Guide

## Automatic Version Management

The release system now **automatically updates** the `versionName` and `versionCode` in `app/build.gradle.kts` based on the release tag. You no longer need to manually edit version numbers!

### How it Works

1. **Auto Release workflow**: Analyzes commits, calculates next version, updates `build.gradle.kts`, builds APK, creates release
2. **Manual tag release**: When you push a tag (e.g., `v1.2.0`), it updates `build.gradle.kts` to match the tag
3. **Local script**: `create-release.sh` now updates the version and commits it before creating the tag

**Version in Settings**: The app will display the correct version in Settings → Info automatically!

## Quick Start: Creating Your First Release

### Option 1: Auto Release (Fully Automated - Recommended)

1. **Merge your changes to `main`**:
   ```bash
   git checkout main
   git merge dev
   git push origin main
   ```

2. **Trigger the Auto Release**:
   - Go to your repository on GitHub
   - Click on **Actions** tab
   - Select **"Auto Release"** workflow on the left
   - Click **"Run workflow"** button (top right)

3. **The workflow automatically**:
   - Analyzes your commits to determine the version bump (major/minor/patch)
   - Calculates the next version number
   - Updates `versionName` and `versionCode` in `build.gradle.kts`
   - Builds the APK with the correct version
   - Creates a GitHub release with categorized release notes
   - Creates a git tag

4. **Check your release**:
   - Go to **Releases** section on GitHub
   - You should see your new release with:
     - Version tag (e.g., `v1.0.0`)
     - Organized release notes by category
     - APK file ready to download

### Option 2: Using Local Script

1. **Prepare your main branch**:
   ```bash
   git checkout main
   git merge dev
   git push origin main
   ```

2. **Create the release**:
   ```bash
   ./scripts/build/create-release.sh minor
   ```
   
   This will:
   - Show you the commits since last release
   - Calculate the next version
   - **Update `versionName` and `versionCode` in `build.gradle.kts`**
   - Commit the version changes
   - Create the git tag
   - Ask for confirmation

3. **Push the changes and tag**:
   ```bash
   git push origin main
   git push origin v1.0.0  # Use the tag that was created
   ```

4. **The GitHub Action will automatically**:
   - Build the APK (with the version already updated)
   - Create the release
   - Generate release notes

### Option 3: Manual Tag (Advanced)

### Option 3: Manual Tag (Advanced)

If you prefer to create tags manually:

1. **Create and push a tag**:
   ```bash
   git tag -a v1.2.0 -m "Release v1.2.0"
   git push origin v1.2.0
   ```

2. **The workflow will**:
   - Extract the version from the tag (e.g., `1.2.0` from `v1.2.0`)
   - Update `versionName` and `versionCode` in `build.gradle.kts`
   - Build the APK
   - Create the release

## For Your v1.0.0 Release

Since you mentioned the v1.0 is ready, here's what to do:

```bash
# Merge dev to main
git checkout main
git merge dev
git push origin main

# Go to GitHub Actions and run "Auto Release" workflow
# It will automatically detect this is the first release and create v1.0.0
```

Or use the script:

```bash
# Merge dev to main
git checkout main
git merge dev
git push origin main

# Create release (will update version, commit, and create tag)
./scripts/build/create-release.sh minor
git push origin main
git push origin v1.0.0
```

## Understanding the Version System

### versionName (User-Facing)
- Displayed in the app (Settings → Info → App Version)
- Follows [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`
- **MAJOR** (X.0.0): Breaking changes
- **MINOR** (0.X.0): New features, backward compatible
- **PATCH** (0.0.X): Bug fixes

### versionCode (Internal)
- Used by Android to manage updates
- Automatically incremented with each release
- Users never see this number

## Release Notes Format

The workflow automatically categorizes commits:

- ✨ **Features** - `feat:` commits
- 🐛 **Bug Fixes** - `fix:` commits
- ♻️ **Refactoring** - `refactor:` commits
- 📚 **Documentation** - `docs:` commits
- 🔧 **Chores** - `chore:` commits

Example commit history will generate:

```
## What's Changed

### ✨ Features
- feat(i18n): add support for English, French, German, Italian, Spanish, and Portuguese languages
- feat(markers): add minute-precision time windows with scroll picker UI

### ♻️ Refactoring
- refactor(ui): implement full-screen map with floating action buttons
- refactor(ui): remove redundant location tracking toggle

### 📚 Documentation
- docs: add MIT license
- docs(settings): update Info section
```

## Troubleshooting

### "No tags found" on first release
- Normal! The workflow will create v1.0.0 as the first release

### APK not attached to release
- Check the Actions tab for build errors
- Make sure the workflow completed successfully

### Version not updated in app
- The workflows automatically update `versionName` in `build.gradle.kts`
- If you see the wrong version, check that the APK was built AFTER the version update step

### Release notes are empty
- Make sure you have commits since the last tag
- Use conventional commit format for better categorization

## Next Steps

After creating v1.0.0:
1. Download the APK from the release page
2. Test installation on a clean device
3. Share the release link with users!
