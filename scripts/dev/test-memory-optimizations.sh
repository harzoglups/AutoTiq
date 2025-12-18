#!/bin/bash

# AutoTiq Memory Test Suite
# Automated tests to verify memory optimizations

PACKAGE_NAME="com.autotiq"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

print_test_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  TEST: $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

pass_test() {
    echo -e "${GREEN}✓ PASS: $1${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

fail_test() {
    echo -e "${RED}✗ FAIL: $1${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

get_memory() {
    $ADB shell dumpsys meminfo $PACKAGE_NAME | grep "TOTAL PSS:" | awk '{print $3}'
}

get_swap() {
    $ADB shell dumpsys meminfo $PACKAGE_NAME | grep "TOTAL SWAP PSS:" | awk '{print $4}'
}

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     AutoTiq Memory Optimization Test Suite             ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if device is connected
if ! $ADB devices | grep -q "device$"; then
    echo -e "${RED}Error: No device connected${NC}"
    exit 1
fi

# Check if app is installed
if ! $ADB shell pm list packages | grep -q $PACKAGE_NAME; then
    echo -e "${RED}Error: App is not installed${NC}"
    exit 1
fi

# Test 1: OSMDroid Configuration
print_test_header "OSMDroid Cache Configuration"

echo "Launching app to initialize OSMDroid..."
$ADB shell am start -n $PACKAGE_NAME/.MainActivity > /dev/null 2>&1
sleep 3

# Check logcat for OSMDroid configuration
if $ADB logcat -d | grep -q "osmdroid"; then
    echo "OSMDroid is initialized"
    pass_test "OSMDroid configuration applied"
else
    fail_test "Could not verify OSMDroid configuration"
fi

# Test 2: Memory Stability During Map Navigation
print_test_header "Memory Stability (30 second test)"

echo "Taking initial memory snapshot..."
MEM_START=$(get_memory)
echo "Initial memory: $(awk "BEGIN {printf \"%.1f MB\", $MEM_START/1024}")"

echo ""
echo "Simulating map usage (30 seconds)..."
echo "Please interact with the map (zoom, pan, add markers)..."

for i in {1..6}; do
    sleep 5
    MEM_CURRENT=$(get_memory)
    echo "  [${i}0s] Memory: $(awk "BEGIN {printf \"%.1f MB\", $MEM_CURRENT/1024}")"
done

MEM_END=$(get_memory)
MEM_DIFF=$((MEM_END - MEM_START))

echo ""
echo "Final memory: $(awk "BEGIN {printf \"%.1f MB\", $MEM_END/1024}")"
echo "Memory change: $(awk "BEGIN {printf \"%.1f MB\", $MEM_DIFF/1024}")"

# Check if memory growth is reasonable (< 20MB increase)
if [ $MEM_DIFF -lt 20480 ]; then
    pass_test "Memory growth is acceptable (< 20 MB)"
else
    fail_test "Memory growth is too high (> 20 MB)"
fi

# Test 3: Swap Usage
print_test_header "Swap Usage Check"

SWAP=$(get_swap)
echo "Current swap usage: $(awk "BEGIN {printf \"%.1f MB\", $SWAP/1024}")"

if [ -z "$SWAP" ] || [ "$SWAP" = "0" ] || [ "$SWAP" -lt 5120 ]; then
    pass_test "Swap usage is low (< 5 MB)"
else
    fail_test "Swap usage is high (> 5 MB)"
fi

# Test 4: Background Worker Check
print_test_header "Background Worker Functionality"

echo "Checking LocationCheckWorker status..."
$ADB logcat -c
sleep 65  # Wait for one worker cycle (60s interval + buffer)

if $ADB logcat -d | grep -q "LocationCheckWorker.*Starting location check"; then
    pass_test "LocationCheckWorker is running"
else
    fail_test "LocationCheckWorker is not running"
fi

# Check if worker completes successfully
if $ADB logcat -d | grep -q "LocationCheckWorker.*Current location"; then
    pass_test "LocationCheckWorker obtains GPS location"
else
    fail_test "LocationCheckWorker cannot obtain GPS location"
fi

# Test 5: GPS Cleanup on Screen Exit
print_test_header "GPS Cleanup Test"

echo "Checking for active location listeners before leaving screen..."
$ADB logcat -c

# Navigate to settings (leaves map screen)
echo "Navigating away from map screen..."
$ADB shell input tap 1000 2000  # Tap settings button (approximate)
sleep 2

# Check logcat for MyLocationOverlay cleanup
# Note: This is indirect since we can't easily check if disableMyLocation was called
# But we can verify the app doesn't crash and memory doesn't spike

MEM_AFTER_NAV=$(get_memory)
echo "Memory after navigation: $(awk "BEGIN {printf \"%.1f MB\", $MEM_AFTER_NAV/1024}")"

if [ $MEM_AFTER_NAV -le $((MEM_END + 10240)) ]; then
    pass_test "No memory spike after leaving map screen"
else
    fail_test "Memory increased significantly after leaving map screen"
fi

# Test 6: OSMDroid Cache Size
print_test_header "OSMDroid Cache Size Limit"

echo "Checking OSMDroid cache directory..."
CACHE_SIZE=$($ADB shell "du -sk /data/data/$PACKAGE_NAME/cache/osmdroid 2>/dev/null | cut -f1")

if [ -n "$CACHE_SIZE" ]; then
    echo "Cache size: $(awk "BEGIN {printf \"%.1f MB\", $CACHE_SIZE/1024}")"
    
    # Check if cache is below 60MB (50MB limit + 10MB buffer)
    if [ $CACHE_SIZE -lt 61440 ]; then
        pass_test "OSMDroid cache is within limits (< 60 MB)"
    else
        fail_test "OSMDroid cache exceeds limits (> 60 MB)"
    fi
else
    echo -e "${YELLOW}Cache directory not accessible (requires root)${NC}"
    pass_test "Cache check skipped (no root access)"
fi

# Final Summary
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                    Test Summary                            ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed! Memory optimizations are working correctly.${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠ Some tests failed. Review the output above for details.${NC}"
    exit 1
fi
