#!/bin/bash
# Deploy AutoTiq Privacy Policy to VPS
# This script should be run after generating a new AAB/APK for release

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PRIVACY_POLICY_FILE="$PROJECT_ROOT/docs/privacy-policy.html"

# Public configuration (not sensitive)
VPS_HOST="cussou.com"
VPS_PATH="~/services/privacy-policies/www/autotiq/"
PRIVACY_POLICY_URL="https://privacy.cussou.com/autotiq/"

# Load VPS_USER from .env file (sensitive: SSH username)
ENV_FILE="$PROJECT_ROOT/.env"
if [ -f "$ENV_FILE" ]; then
    # Source .env file (safer than export with xargs for paths with spaces)
    set -a
    source "$ENV_FILE"
    set +a
else
    echo "⚠️  Warning: .env file not found at $ENV_FILE"
    echo "    Copy .env.example to .env and set your VPS_USER"
    echo "    Using default value (this will likely fail)"
fi

# Use VPS_USER from .env with fallback default
VPS_USER="${VPS_USER:-your-username}"

echo "=== AutoTiq Privacy Policy Deployment ==="
echo ""

# Check if privacy policy file exists
if [ ! -f "$PRIVACY_POLICY_FILE" ]; then
    echo "❌ Error: Privacy policy file not found at $PRIVACY_POLICY_FILE"
    exit 1
fi

# Extract version from build.gradle.kts
VERSION=$(grep "versionName" "$PROJECT_ROOT/app/build.gradle.kts" | sed 's/.*versionName = "\(.*\)"/\1/')
if [ -z "$VERSION" ]; then
    echo "⚠️  Warning: Could not extract version from build.gradle.kts"
    VERSION="unknown"
fi

echo "📦 Application Version: $VERSION"
echo "📄 Privacy Policy: $PRIVACY_POLICY_FILE"
echo "🚀 Deploying to: $VPS_USER@$VPS_HOST:$VPS_PATH"
echo ""

# Update version in HTML file if needed
CURRENT_DATE=$(date +"%B %d, %Y")
echo "📅 Updating date to: $CURRENT_DATE"
echo "🏷️  Updating version to: $VERSION"

# Create temp file with updated version and date
sed -e "s/<p><strong>Last Updated<\/strong>:.*<\/p>/<p><strong>Last Updated<\/strong>: $CURRENT_DATE<\/p>/" \
    -e "s/<p><strong>Application Version<\/strong>:.*<\/p>/<p><strong>Application Version<\/strong>: $VERSION<\/p>/" \
    -e "s/<p><strong>Effective Date<\/strong>:.*<\/p>/<p><strong>Effective Date<\/strong>: $CURRENT_DATE<\/p>/" \
    "$PRIVACY_POLICY_FILE" > "$PRIVACY_POLICY_FILE.tmp"

mv "$PRIVACY_POLICY_FILE.tmp" "$PRIVACY_POLICY_FILE"

# Deploy to VPS
echo ""
echo "🔄 Copying to VPS..."
scp "$PRIVACY_POLICY_FILE" "$VPS_USER@$VPS_HOST:$VPS_PATH/index.html"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Privacy policy deployed successfully!"
    echo "🔗 URL: $PRIVACY_POLICY_URL"
    echo ""
    echo "🧪 Testing access..."
    sleep 2
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$PRIVACY_POLICY_URL")
    if [ "$HTTP_CODE" = "200" ]; then
        echo "✅ Privacy policy is accessible (HTTP $HTTP_CODE)"
    else
        echo "⚠️  Warning: Unexpected HTTP code: $HTTP_CODE"
    fi
else
    echo ""
    echo "❌ Deployment failed!"
    exit 1
fi

echo ""
echo "=== Deployment Complete ==="
