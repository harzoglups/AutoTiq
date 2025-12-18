# AutoTiq Memory Monitoring Tools

This directory contains tools to monitor and analyze memory usage of the AutoTiq app.

## Available Scripts

### 1. monitor-memory.sh - Real-time Memory Monitor

**Purpose:** Provides real-time dashboard of memory usage, CPU, and worker status.

**Usage:**
```bash
./scripts/dev/monitor-memory.sh [interval_seconds]
```

**Examples:**
```bash
# Monitor every 5 seconds (default)
./scripts/dev/monitor-memory.sh

# Monitor every 10 seconds
./scripts/dev/monitor-memory.sh 10

# Monitor every 2 seconds (intensive)
./scripts/dev/monitor-memory.sh 2
```

**Output:**
- Real-time dashboard showing:
  - Memory breakdown (Native, Dalvik, Graphics, etc.)
  - Memory trend (Increasing/Decreasing/Stable)
  - CPU usage
  - Background worker status
  - Swap usage warnings
- CSV log file: `autotiq_monitor_YYYYMMDD_HHMMSS.csv`

**When to use:**
- During development to spot memory leaks
- After making changes to verify memory impact
- Long-term monitoring (run overnight to catch slow leaks)
- Performance testing

---

### 2. memory-snapshot.sh - Memory Snapshot

**Purpose:** Takes a detailed memory snapshot at a specific moment.

**Usage:**
```bash
./scripts/dev/memory-snapshot.sh
```

**Output:**
- Creates `memory_snapshots/` directory with:
  - Full meminfo dump
  - Memory pressure info
  - Process details
  - Worker logs
  - OSMDroid cache size
  - System memory status

**When to use:**
- Before and after a test session (compare snapshots)
- When investigating an ANR
- To document memory state for bug reports

---

### 3. test-memory-optimizations.sh - Automated Test Suite

**Purpose:** Runs automated tests to verify memory optimizations are working.

**Usage:**
```bash
./scripts/dev/test-memory-optimizations.sh
```

**Tests performed:**
1. OSMDroid configuration check
2. Memory stability during 30s usage
3. Swap usage verification
4. Background worker functionality
5. GPS cleanup on screen exit
6. OSMDroid cache size limits

**Exit codes:**
- 0: All tests passed
- 1: Some tests failed

**When to use:**
- After implementing memory optimizations
- Before releasing a new version
- In CI/CD pipeline
- After major refactoring

---

## Memory Optimizations Applied

### 1. OSMDroid Cache Limiting
- **File:** `AutoTiqApplication.kt`
- **Changes:**
  - Disk cache: 50 MB max (trim to 40 MB)
  - Tile expiration: 7 days
- **Impact:** Reduces memory footprint by ~50%

### 2. MapView Resource Cleanup
- **File:** `MapScreen.kt` (DisposableEffect)
- **Changes:**
  - GPS overlay stopped on screen exit
  - All bitmaps recycled
  - Overlays cleared
- **Impact:** Prevents GPS battery drain, frees ~10-20 MB per session

### 3. Marker Bitmap Recycling
- **File:** `MapScreen.kt` (update block)
- **Changes:**
  - Bitmaps recycled before removal
  - Search markers cleaned up properly
- **Impact:** Immediate memory release instead of waiting for GC

### 4. MapView Optimization
- **File:** `MapScreen.kt` (factory block)
- **Changes:**
  - Scaled tiles for DPI
  - Optimized rendering settings
- **Impact:** Lower memory usage on high-DPI screens

---

## How to Investigate ANRs

If you experience an ANR (Application Not Responding):

1. **Check if ANR traces are still available:**
   ```bash
   adb logcat -d | grep ANR
   adb shell dumpsys dropbox --print data_app_anr | grep com.autotiq
   ```

2. **Take a memory snapshot immediately:**
   ```bash
   ./scripts/dev/memory-snapshot.sh
   ```

3. **Check for memory pressure:**
   ```bash
   adb shell cat /proc/pressure/memory
   ```

4. **Look for high swap usage:**
   ```bash
   adb shell dumpsys meminfo com.autotiq | grep SWAP
   ```

5. **Review recent worker activity:**
   ```bash
   adb logcat -d -s LocationCheckWorker:* | tail -50
   ```

---

## Expected Memory Baseline

### After Fresh Start
- **Total PSS:** ~120-150 MB
- **Native Heap:** ~30-40 MB
- **Dalvik Heap:** ~20-30 MB
- **Graphics:** ~20-30 MB
- **Swap:** 0-5 MB

### During Map Usage
- **Total PSS:** ~180-220 MB (with map tiles loaded)
- **Swap:** Should stay < 10 MB

### Warning Signs
- ⚠️ **Swap > 50 MB:** Memory pressure, possible leak
- ⚠️ **Total PSS > 300 MB:** High memory usage
- ⚠️ **Graphics > 100 MB:** Too many tiles/bitmaps cached

---

## Troubleshooting

### Script shows "0 MB" for all values
- **Cause:** App is not running
- **Solution:** Launch the app first, then run the script

### "Permission denied" errors
- **Cause:** Some memory files require root access
- **Solution:** These are informational only, script will continue

### AWK syntax errors
- **Cause:** macOS uses BSD awk (limited features)
- **Solution:** The scripts have been updated to work with BSD awk

### Device not detected
- **Cause:** ADB not configured or device not connected
- **Solution:** Check USB connection and run `adb devices`

---

## Tips for Long-term Monitoring

1. **Run overnight test:**
   ```bash
   nohup ./scripts/dev/monitor-memory.sh 60 > monitor.log 2>&1 &
   ```

2. **Compare snapshots:**
   ```bash
   ./scripts/dev/memory-snapshot.sh  # Before test
   # ... use the app for 2 hours ...
   ./scripts/dev/memory-snapshot.sh  # After test
   # Compare the two meminfo files
   ```

3. **Analyze CSV data:**
   Open `autotiq_monitor_*.csv` in Excel/Sheets to:
   - Plot memory trends over time
   - Identify memory spikes
   - Correlate with user actions

4. **Monitor specific operations:**
   ```bash
   # Start monitoring
   ./scripts/dev/monitor-memory.sh 5
   # Then perform specific actions:
   # - Add 10 markers
   # - Navigate map for 5 minutes
   # - Switch between Street/Topo layers
   # - Create/delete markers repeatedly
   ```

---

## See Also

- [DEVELOPMENT.md](DEVELOPMENT.md) - Development guidelines
- [AGENTS.md](AGENTS.md) - Agent commit policies
- ANR analysis document (see logs from December 16, 2025)
