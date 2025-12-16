#!/bin/bash

# FairLaunch Memory Snapshot Tool
# Takes a detailed memory snapshot for analysis

PACKAGE_NAME="com.fairlaunch"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
OUTPUT_DIR="memory_snapshots"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

mkdir -p $OUTPUT_DIR

echo -e "${BLUE}Taking memory snapshot for FairLaunch...${NC}"
echo ""

# Check if app is running
if ! $ADB shell "ps -A | grep $PACKAGE_NAME" > /dev/null 2>&1; then
    echo -e "${YELLOW}Warning: App is not running${NC}"
fi

# 1. Full meminfo dump
echo -e "${GREEN}[1/6] Getting detailed memory info...${NC}"
$ADB shell dumpsys meminfo $PACKAGE_NAME > "$OUTPUT_DIR/meminfo_$TIMESTAMP.txt"

# 2. Memory pressure info
echo -e "${GREEN}[2/6] Checking memory pressure...${NC}"
$ADB shell cat /proc/pressure/memory > "$OUTPUT_DIR/pressure_memory_$TIMESTAMP.txt" 2>/dev/null || echo "Memory pressure info not available"

# 3. App process info
echo -e "${GREEN}[3/6] Getting process info...${NC}"
$ADB shell "ps -A | grep $PACKAGE_NAME" > "$OUTPUT_DIR/process_info_$TIMESTAMP.txt"

# 4. Recent location worker logs
echo -e "${GREEN}[4/6] Extracting location worker logs...${NC}"
$ADB logcat -d -s LocationCheckWorker:* > "$OUTPUT_DIR/worker_logs_$TIMESTAMP.txt"

# 5. OSMDroid cache info
echo -e "${GREEN}[5/6] Checking OSMDroid cache...${NC}"
$ADB shell "du -sh /data/data/$PACKAGE_NAME/cache/osmdroid 2>/dev/null || echo 'Cache not found'" > "$OUTPUT_DIR/osmdroid_cache_$TIMESTAMP.txt"
$ADB shell "ls -lh /data/data/$PACKAGE_NAME/cache/osmdroid 2>/dev/null || echo 'Cache not accessible'" >> "$OUTPUT_DIR/osmdroid_cache_$TIMESTAMP.txt"

# 6. System memory status
echo -e "${GREEN}[6/6] Getting system memory status...${NC}"
$ADB shell cat /proc/meminfo > "$OUTPUT_DIR/system_meminfo_$TIMESTAMP.txt"

# Parse and display summary
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}              Memory Snapshot Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""

# Extract key metrics
TOTAL_PSS=$(grep "TOTAL PSS:" "$OUTPUT_DIR/meminfo_$TIMESTAMP.txt" | awk '{print $3}')
NATIVE_HEAP=$(grep "Native Heap" "$OUTPUT_DIR/meminfo_$TIMESTAMP.txt" | tail -1 | awk '{print $3}')
DALVIK_HEAP=$(grep "Dalvik Heap" "$OUTPUT_DIR/meminfo_$TIMESTAMP.txt" | tail -1 | awk '{print $3}')
GRAPHICS=$(grep "Graphics" "$OUTPUT_DIR/meminfo_$TIMESTAMP.txt" | tail -1 | awk '{print $3}')
SWAP=$(grep "TOTAL SWAP PSS:" "$OUTPUT_DIR/meminfo_$TIMESTAMP.txt" | awk '{print $4}')

echo "Total PSS:      $(echo "$TOTAL_PSS" | awk '{printf "%.1f MB", $1/1024}')"
echo "Native Heap:    $(echo "$NATIVE_HEAP" | awk '{printf "%.1f MB", $1/1024}')"
echo "Dalvik Heap:    $(echo "$DALVIK_HEAP" | awk '{printf "%.1f MB", $1/1024}')"
echo "Graphics:       $(echo "$GRAPHICS" | awk '{printf "%.1f MB", $1/1024}')"

if [ -n "$SWAP" ] && [ "$SWAP" != "0" ] && [ "$SWAP" != "TOTAL" ]; then
    echo -e "${YELLOW}Swap:           $(echo "$SWAP" | awk '{printf "%.1f MB", $1/1024}')${NC}"
fi

echo ""
echo "Files saved to: $OUTPUT_DIR/"
echo "  - meminfo_$TIMESTAMP.txt"
echo "  - pressure_memory_$TIMESTAMP.txt"
echo "  - process_info_$TIMESTAMP.txt"
echo "  - worker_logs_$TIMESTAMP.txt"
echo "  - osmdroid_cache_$TIMESTAMP.txt"
echo "  - system_meminfo_$TIMESTAMP.txt"

echo ""
echo -e "${GREEN}✓ Snapshot complete!${NC}"
