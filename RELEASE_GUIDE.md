# Release Guide

## Quick Start: Creating Your First Release

### Option 1: Using GitHub Actions (Recommended)

1. **Merge your changes to `main`**:
   ```bash
   git checkout main
   git merge dev
   git push origin main
   ```

2. **Create the release via GitHub**:
   - Go to your repository on GitHub
   - Click on **Actions** tab
   - Select **"Create Release"** workflow on the left
   - Click **"Run workflow"** button (top right)
   - Choose the branch: `main`
   - Select version bump type:
     - `major` - Breaking changes (1.0.0 → 2.0.0)
     - `minor` - New features (1.0.0 → 1.1.0) ⭐ **Recommended for v1.0.0**
     - `patch` - Bug fixes (1.0.0 → 1.0.1)
   - Click **"Run workflow"**

3. **Wait for the build** (2-3 minutes):
   - The workflow will automatically:
     - Calculate version number
     - Generate release notes from commits
     - Build the APK
     - Create a GitHub release with the APK attached

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

2. **Create the tag locally**:
   ```bash
   ./create-release.sh minor
   ```
   
   This will:
   - Show you the commits since last release
   - Calculate the next version
   - Ask for confirmation

3. **Push the tag**:
   ```bash
   git push origin v1.0.0  # Use the tag that was created
   ```

4. **The GitHub Action will automatically**:
   - Build the APK
   - Create the release
   - Generate release notes

## For Your v1.0.0 Release

Since you mentioned the v1.0 is ready, here's what to do:

```bash
# 1. Merge dev to main
git checkout main
git merge dev
git push origin main

# 2. Go to GitHub Actions and run "Create Release" workflow
#    Select "minor" bump type (this will create v1.0.0 if no previous tag)
```

Or use the script:

```bash
# 1. Merge dev to main
git checkout main
git merge dev
git push origin main

# 2. Create and push tag
./create-release.sh minor
git push origin v1.0.0
```

## Understanding Version Numbers

Following [Semantic Versioning](https://semver.org/):

- **MAJOR** (X.0.0): Incompatible API changes, major breaking changes
- **MINOR** (0.X.0): New features, backward compatible
- **PATCH** (0.0.X): Bug fixes, backward compatible

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

### Release notes are empty
- Make sure you have commits since the last tag
- Use conventional commit format for better categorization

## Next Steps

After creating v1.0.0:
1. Download the APK from the release page
2. Test installation on a clean device
3. Share the release link with users!
