#!/bin/bash

# AutoTiq Play Store Builder
# Automatically calculates version from git history and builds signed AAB for Play Store
# Uses same versioning logic as GitHub Actions workflow for consistency

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Configuration
BUILD_GRADLE="app/build.gradle.kts"
KEYSTORE_PROPS="keystore.properties"

# Parse command line arguments
DRY_RUN=false
FORCE_VERSION=""
SKIP_COMMIT=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --version)
      FORCE_VERSION="$2"
      shift 2
      ;;
    --skip-commit)
      SKIP_COMMIT=true
      shift
      ;;
    --help)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --dry-run          Show what would happen without building"
      echo "  --version VERSION  Force specific version (e.g., 1.0.0)"
      echo "  --skip-commit      Don't commit version changes to git"
      echo "  --help             Show this help message"
      echo ""
      echo "Examples:"
      echo "  $0                          # Normal build with auto version"
      echo "  $0 --dry-run                # Preview without building"
      echo "  $0 --version 1.0.0          # Force version 1.0.0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Run with --help for usage information"
      exit 1
      ;;
  esac
done

# Print header
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}🏗️  AutoTiq Play Store Builder${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if build.gradle.kts exists
if [ ! -f "$BUILD_GRADLE" ]; then
  echo -e "${RED}❌ Error: $BUILD_GRADLE not found${NC}"
  exit 1
fi

# Get current version from build.gradle.kts
CURRENT_VERSION_NAME=$(grep "versionName = " "$BUILD_GRADLE" | sed 's/.*versionName = "\(.*\)".*/\1/')
CURRENT_VERSION_CODE=$(grep "versionCode = " "$BUILD_GRADLE" | sed 's/.*versionCode = \([0-9]*\).*/\1/')

echo -e "${BLUE}📋 Current build.gradle.kts:${NC}"
echo "   versionName: $CURRENT_VERSION_NAME"
echo "   versionCode: $CURRENT_VERSION_CODE"
echo ""

# Calculate next version if not forced
if [ -n "$FORCE_VERSION" ]; then
  # Use forced version
  NEW_VERSION_NAME="$FORCE_VERSION"
  BUMP_TYPE="forced"
  echo -e "${YELLOW}⚠️  Using forced version: $NEW_VERSION_NAME${NC}"
  echo ""
else
  # Auto-detect version from git history
  echo -e "${BLUE}🔍 Analyzing git history...${NC}"
  
  # Get latest tag
  LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
  echo "📌 Latest tag: $LATEST_TAG"
  
  # Get commits since last tag
  if [ "$LATEST_TAG" == "v0.0.0" ]; then
    COMMITS=$(git log --pretty=format:"- %s" --no-merges)
  else
    COMMITS=$(git log ${LATEST_TAG}..HEAD --pretty=format:"- %s" --no-merges)
  fi
  
  # Check if there are any commits
  if [ -z "$COMMITS" ]; then
    echo -e "${RED}❌ Error: No new commits since last tag${NC}"
    echo "   Nothing to release!"
    exit 1
  fi
  
  COMMIT_COUNT=$(echo "$COMMITS" | wc -l | tr -d ' ')
  echo "📝 Commits since tag: $COMMIT_COUNT commits"
  echo ""
  
  # Show first few commits
  echo -e "${BLUE}Recent commits:${NC}"
  echo "$COMMITS" | head -5
  if [ "$COMMIT_COUNT" -gt 5 ]; then
    echo "   ... and $((COMMIT_COUNT - 5)) more"
  fi
  echo ""
  
  # Determine bump type based on conventional commits
  BUMP_TYPE="patch"
  
  # Check for breaking changes (major bump)
  if echo "$COMMITS" | grep -qiE "^- (feat|fix|chore|refactor|docs)(\(.+\))?!:|BREAKING CHANGE:|breaking:|^- break:"; then
    BUMP_TYPE="major"
    echo -e "${RED}🚨 Breaking changes detected → MAJOR version bump${NC}"
  # Check for features (minor bump)
  elif echo "$COMMITS" | grep -qE "^- feat(\(.+\))?:"; then
    BUMP_TYPE="minor"
    echo -e "${GREEN}✨ Features detected → MINOR version bump${NC}"
  # Check for fixes, docs, refactor, chore (patch bump)
  elif echo "$COMMITS" | grep -qE "^- (fix|docs|refactor|chore|style|test|perf)(\(.+\))?:"; then
    BUMP_TYPE="patch"
    echo -e "${YELLOW}🐛 Fixes/improvements detected → PATCH version bump${NC}"
  else
    echo -e "${YELLOW}⚠️  No conventional commits detected, defaulting to PATCH bump${NC}"
    BUMP_TYPE="patch"
  fi
  echo ""
  
  # Calculate next version
  VERSION=${LATEST_TAG#v}
  IFS='.' read -ra VERSION_PARTS <<< "$VERSION"
  MAJOR=${VERSION_PARTS[0]:-0}
  MINOR=${VERSION_PARTS[1]:-0}
  PATCH=${VERSION_PARTS[2]:-0}
  
  case "$BUMP_TYPE" in
    major)
      MAJOR=$((MAJOR + 1))
      MINOR=0
      PATCH=0
      ;;
    minor)
      MINOR=$((MINOR + 1))
      PATCH=0
      ;;
    patch)
      PATCH=$((PATCH + 1))
      ;;
  esac
  
  NEW_VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"
  echo -e "${BOLD}📦 Next version: v${NEW_VERSION_NAME} (${BUMP_TYPE} bump)${NC}"
  echo ""
fi

# Calculate new version code
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

# Summary
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}📊 Version Summary${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}versionName:${NC} $CURRENT_VERSION_NAME → ${GREEN}$NEW_VERSION_NAME${NC}"
echo -e "${YELLOW}versionCode:${NC} $CURRENT_VERSION_CODE → ${GREEN}$NEW_VERSION_CODE${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Dry run mode - stop here
if [ "$DRY_RUN" = true ]; then
  echo -e "${YELLOW}🔍 DRY RUN MODE - No changes made${NC}"
  echo ""
  echo "Would perform:"
  echo "  1. Update $BUILD_GRADLE"
  echo "  2. Commit: 'chore: bump version to $NEW_VERSION_NAME for Play Store release'"
  echo "  3. Build AAB: ./gradlew bundleRelease"
  echo "  4. Copy to: AutoTiq-v${NEW_VERSION_NAME}.aab"
  echo ""
  exit 0
fi

# Update build.gradle.kts
echo -e "${BLUE}🔄 Updating $BUILD_GRADLE...${NC}"

# Create backup
cp "$BUILD_GRADLE" "${BUILD_GRADLE}.bak"

# Update versionName
sed -i.tmp "s/versionName = \".*\"/versionName = \"$NEW_VERSION_NAME\"/" "$BUILD_GRADLE"

# Update versionCode
sed -i.tmp "s/versionCode = [0-9]*/versionCode = $NEW_VERSION_CODE/" "$BUILD_GRADLE"

# Remove temporary files
rm -f "${BUILD_GRADLE}.tmp"

echo -e "${GREEN}✅ Version updated in $BUILD_GRADLE${NC}"
echo ""

# Commit version changes
if [ "$SKIP_COMMIT" = false ]; then
  echo -e "${BLUE}📝 Committing version changes...${NC}"
  git add "$BUILD_GRADLE"
  git commit -m "chore: bump version to $NEW_VERSION_NAME for Play Store release"
  echo -e "${GREEN}✅ Version committed to git${NC}"
  echo ""
else
  echo -e "${YELLOW}⚠️  Skipping git commit (--skip-commit flag)${NC}"
  echo ""
fi

# Check for keystore
if [ ! -f "$KEYSTORE_PROPS" ]; then
  echo -e "${YELLOW}⚠️  Warning: $KEYSTORE_PROPS not found${NC}"
  echo "   Building with debug keystore (for testing only)"
  echo "   For production release, create $KEYSTORE_PROPS first"
  echo "   See PLAY_STORE_RELEASE.md for instructions"
  echo ""
fi

# Set JAVA_HOME for macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Build AAB
echo -e "${BLUE}🏗️  Building AAB...${NC}"
echo "   This may take a few minutes..."
echo ""

# Run gradle build
if ./gradlew bundleRelease; then
  echo ""
  echo -e "${GREEN}✅ AAB built successfully!${NC}"
  echo ""
else
  echo ""
  echo -e "${RED}❌ Build failed!${NC}"
  echo ""
  echo "Restoring backup..."
  mv "${BUILD_GRADLE}.bak" "$BUILD_GRADLE"
  echo -e "${YELLOW}⚠️  Version changes reverted${NC}"
  exit 1
fi

# Remove backup
rm -f "${BUILD_GRADLE}.bak"

# Copy AAB with versioned filename
AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
OUTPUT_AAB="AutoTiq-v${NEW_VERSION_NAME}.aab"

if [ -f "$AAB_PATH" ]; then
  cp "$AAB_PATH" "$OUTPUT_AAB"
  AAB_SIZE=$(du -h "$OUTPUT_AAB" | cut -f1)
  echo -e "${GREEN}📦 Output: $OUTPUT_AAB ($AAB_SIZE)${NC}"
  echo ""
else
  echo -e "${RED}❌ Error: AAB not found at $AAB_PATH${NC}"
  exit 1
fi

# Success summary
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}${GREEN}🎉 READY FOR PLAY STORE UPLOAD${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BOLD}Next steps:${NC}"
echo ""
echo "  1. Upload AAB to Play Console:"
echo "     → https://play.google.com/console"
echo "     → Upload: $OUTPUT_AAB"
echo ""
echo "  2. Optional: Create git tag"
echo "     → git tag v${NEW_VERSION_NAME}"
echo "     → git push origin v${NEW_VERSION_NAME}"
echo ""
echo "  3. Optional: Push version commit"
echo "     → git push origin main"
echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Show file location
echo -e "${BLUE}📁 File ready at:${NC} $(pwd)/$OUTPUT_AAB"
echo ""
