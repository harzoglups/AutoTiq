#!/bin/bash

# FairLaunch Memory & Performance Monitor
# Usage: ./monitor-memory.sh [interval_seconds]

PACKAGE_NAME="com.fairlaunch"
INTERVAL=${1:-5}  # Default: 5 seconds between measurements
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Check if device is connected
if ! $ADB devices | grep -q "device$"; then
    echo -e "${RED}Error: No device connected${NC}"
    exit 1
fi

# Check if app is running
check_app_running() {
    $ADB shell "ps -A | grep $PACKAGE_NAME" > /dev/null 2>&1
    return $?
}

# Get memory info
get_memory_info() {
    $ADB shell dumpsys meminfo $PACKAGE_NAME | awk '
    /TOTAL PSS/ { total_pss = $3 }
    /Native Heap/ { getline; native = $3 }
    /Dalvik Heap/ { getline; dalvik = $3 }
    /Graphics/ { getline; graphics = $3 }
    /Private Other/ { getline; other = $3 }
    /^[ \t]*System/ { getline; sys = $3 }
    /TOTAL SWAP PSS/ { swap = $4 }
    END {
        print total_pss, native, dalvik, graphics, other, sys, swap
    }'
}

# Get CPU info
get_cpu_info() {
    $ADB shell top -n 1 -b | grep $PACKAGE_NAME | head -1 | awk '{print $9}'
}

# Get location provider status
get_location_status() {
    $ADB shell dumpsys location | grep -A 10 "com.fairlaunch" | grep -E "(GPS|NETWORK|FUSED)" | head -3
}

# Get worker status
get_worker_status() {
    $ADB logcat -d -s LocationCheckWorker:* | tail -5
}

# Clear screen and print header
print_header() {
    clear
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║           FairLaunch Memory & Performance Monitor (${INTERVAL}s interval)            ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# Format memory value (KB to MB)
format_memory() {
    local kb=$1
    if [ -z "$kb" ] || [ "$kb" = "0" ]; then
        echo "0 MB"
    else
        echo "$(awk "BEGIN {printf \"%.1f MB\", $kb/1024}")"
    fi
}

# Monitor loop
echo -e "${GREEN}Starting FairLaunch monitor... Press Ctrl+C to stop${NC}"
echo ""

# Initialize counters
measurement_count=0
total_pss_start=0
max_pss=0
min_pss=999999999

# CSV log file
LOG_FILE="fairlaunch_monitor_$(date +%Y%m%d_%H%M%S).csv"
echo "Timestamp,Total_PSS_KB,Native_KB,Dalvik_KB,Graphics_KB,Other_KB,System_KB,Swap_KB,CPU_%" > $LOG_FILE

while true; do
    if ! check_app_running; then
        print_header
        echo -e "${RED}⚠️  App is not running${NC}"
        echo -e "${YELLOW}Waiting for app to start...${NC}"
        sleep $INTERVAL
        continue
    fi
    
    # Get metrics
    memory_data=$(get_memory_info)
    read -r total_pss native dalvik graphics other sys swap <<< "$memory_data"
    cpu=$(get_cpu_info)
    
    # Handle empty values
    total_pss=${total_pss:-0}
    native=${native:-0}
    dalvik=${dalvik:-0}
    graphics=${graphics:-0}
    other=${other:-0}
    sys=${sys:-0}
    swap=${swap:-0}
    cpu=${cpu:-0}
    
    # Track stats
    measurement_count=$((measurement_count + 1))
    if [ $measurement_count -eq 1 ]; then
        total_pss_start=$total_pss
    fi
    
    if [ $total_pss -gt $max_pss ]; then
        max_pss=$total_pss
    fi
    
    if [ $total_pss -lt $min_pss ] && [ $total_pss -gt 0 ]; then
        min_pss=$total_pss
    fi
    
    # Calculate memory change
    mem_change=$((total_pss - total_pss_start))
    
    # Determine memory trend
    if [ $mem_change -gt 5000 ]; then
        trend="${RED}↑ INCREASING${NC}"
    elif [ $mem_change -lt -5000 ]; then
        trend="${GREEN}↓ DECREASING${NC}"
    else
        trend="${GREEN}→ STABLE${NC}"
    fi
    
    # Print dashboard
    print_header
    
    echo -e "${BLUE}📊 Memory Usage (PSS - Proportional Set Size)${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "%-20s %15s\n" "Total PSS:" "$(format_memory $total_pss)"
    printf "%-20s %15s\n" "  Native Heap:" "$(format_memory $native)"
    printf "%-20s %15s\n" "  Dalvik Heap:" "$(format_memory $dalvik)"
    printf "%-20s %15s\n" "  Graphics:" "$(format_memory $graphics)"
    printf "%-20s %15s\n" "  Private Other:" "$(format_memory $other)"
    printf "%-20s %15s\n" "  System:" "$(format_memory $sys)"
    
    if [ "$swap" != "0" ] && [ -n "$swap" ]; then
        echo ""
        printf "${YELLOW}%-20s %15s${NC}\n" "⚠️  Swap:" "$(format_memory $swap)"
    fi
    
    echo ""
    echo -e "${BLUE}📈 Memory Statistics${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "%-20s %15s\n" "Start:" "$(format_memory $total_pss_start)"
    printf "%-20s %15s\n" "Current:" "$(format_memory $total_pss)"
    printf "%-20s %15s\n" "Change:" "$(format_memory $mem_change)"
    printf "%-20s %15s\n" "Min:" "$(format_memory $min_pss)"
    printf "%-20s %15s\n" "Max:" "$(format_memory $max_pss)"
    printf "%-20s " "Trend:"
    echo -e "$trend"
    
    echo ""
    echo -e "${BLUE}⚡ CPU Usage${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Color code CPU usage
    if (( $(echo "$cpu > 50" | bc -l 2>/dev/null || echo 0) )); then
        cpu_color=$RED
    elif (( $(echo "$cpu > 20" | bc -l 2>/dev/null || echo 0) )); then
        cpu_color=$YELLOW
    else
        cpu_color=$GREEN
    fi
    
    echo -e "Current: ${cpu_color}${cpu}%${NC}"
    
    echo ""
    echo -e "${BLUE}🔄 Background Worker Status${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Get last worker execution
    last_worker=$($ADB logcat -d -s LocationCheckWorker:D | tail -3 | sed 's/^.*LocationCheckWorker: //')
    if [ -n "$last_worker" ]; then
        echo "$last_worker" | while IFS= read -r line; do
            echo -e "${GREEN}  $line${NC}"
        done
    else
        echo -e "${YELLOW}  No recent worker activity${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}📝 Session Info${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "%-20s %15s\n" "Measurements:" "$measurement_count"
    printf "%-20s %15s\n" "Interval:" "${INTERVAL}s"
    printf "%-20s %15s\n" "Log file:" "$LOG_FILE"
    printf "%-20s %15s\n" "Time:" "$(date '+%H:%M:%S')"
    
    # Memory health indicator
    echo ""
    if [ "$swap" != "0" ] && [ -n "$swap" ] && [ $swap -gt 10000 ]; then
        echo -e "${RED}⚠️  WARNING: High swap usage detected! App may be experiencing memory pressure.${NC}"
    elif [ $total_pss -gt 200000 ]; then
        echo -e "${YELLOW}⚠️  Memory usage is high (>200 MB). Monitor for leaks.${NC}"
    else
        echo -e "${GREEN}✓ Memory usage is healthy${NC}"
    fi
    
    # Save to CSV
    echo "$(date '+%Y-%m-%d %H:%M:%S'),$total_pss,$native,$dalvik,$graphics,$other,$sys,$swap,$cpu" >> $LOG_FILE
    
    sleep $INTERVAL
done
