package com.cussou.autotiq.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.cussou.autotiq.R
import com.cussou.autotiq.domain.usecase.CheckProximityUseCase
import com.cussou.autotiq.domain.usecase.GetSettingsUseCase
import com.cussou.autotiq.geofence.GeofenceHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@HiltWorker
class LocationCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val checkProximityUseCase: CheckProximityUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val getMapPointsUseCase: com.cussou.autotiq.domain.usecase.GetMapPointsUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LocationCheckWorker"
        const val WORK_NAME = "location_check_work"
        private const val LOCATION_TIMEOUT_MS = 30000L // 30 seconds timeout for GPS cold start
    }

    /**
     * Required for expedited work on Android < 12.
     * WorkManager calls this to get the foreground notification when running as expedited.
     * On Android 12+, expedited jobs use JobScheduler's expedited quota instead.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        GeofenceHelper.createExpeditedChannel(context)
        
        val notification = NotificationCompat.Builder(context, GeofenceHelper.EXPEDITED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Checking location...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        return ForegroundInfo(GeofenceHelper.EXPEDITED_NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting location check...")

        // Check location permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return Result.failure()
        }

        try {
            // Get settings first
            val settings = getSettingsUseCase().first()
            Log.d(TAG, "Settings: interval=${settings.checkIntervalSeconds}s, distance=${settings.proximityDistanceMeters}m, enabled=${settings.isLocationTrackingEnabled}")
            
            // If tracking is disabled, don't reschedule
            if (!settings.isLocationTrackingEnabled) {
                Log.d(TAG, "Tracking disabled, stopping worker")
                return Result.success()
            }
            
            // Check if today is an active weekday
            val calendar = java.util.Calendar.getInstance()
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            // Convert Calendar day (1=Sunday, 2=Monday, ..., 7=Saturday) to ISO day (1=Monday, ..., 7=Sunday)
            val isoDayOfWeek = if (currentDayOfWeek == java.util.Calendar.SUNDAY) 7 else currentDayOfWeek - 1
            
            if (!settings.activeWeekdays.contains(isoDayOfWeek)) {
                Log.d(TAG, "Today (ISO day $isoDayOfWeek) is not an active day, skipping check. Active days: ${settings.activeWeekdays}")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return Result.success()
            }
            
            Log.d(TAG, "Today (ISO day $isoDayOfWeek) is an active day, checking time windows...")
            
            // Get all map points to check time windows
            val allPoints = getMapPointsUseCase().first()
            
            if (allPoints.isEmpty()) {
                Log.d(TAG, "No map points defined, skipping check")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return Result.success()
            }
            
            // Check if current time is within any point's time window
            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
            
            val isWithinAnyTimeWindow = allPoints.any { point ->
                isWithinTimeWindow(currentHour, currentMinute, point.startHour, point.startMinute, point.endHour, point.endMinute)
            }
            
            if (!isWithinAnyTimeWindow) {
                Log.d(TAG, "Current time $currentHour:${currentMinute.toString().padStart(2, '0')} is outside all marker time windows, skipping GPS scan")
                allPoints.forEach { point ->
                    Log.d(TAG, "  '${point.name}': ${point.startHour}:${point.startMinute.toString().padStart(2, '0')}-${point.endHour}:${point.endMinute.toString().padStart(2, '0')}")
                }
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return Result.success()
            }
            
            Log.d(TAG, "Current time is within at least one marker's time window, proceeding with GPS scan")
            
            // Get current location with fresh request
            val location = getCurrentLocation(settings.checkIntervalSeconds)

            if (location == null) {
                Log.w(TAG, "Location is null after timeout, will retry at next interval")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return Result.success()
            }

            Log.d(TAG, "Current location: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
            
            // Check proximity - returns list of points we've entered
            val checkResult = checkProximityUseCase.invokeWithDetails(
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                proximityDistanceMeters = settings.proximityDistanceMeters
            )
            
            // Log detailed information about all points
            Log.d(TAG, "Proximity check for ${checkResult.allPointsDetails.size} points (threshold: ${settings.proximityDistanceMeters}m, testMode: ${settings.testModeEnabled}):")
            checkResult.allPointsDetails.forEach { detail ->
                Log.d(TAG, "  '${detail.point.name}': distance=${detail.distance.toInt()}m, isInside=${detail.isInside}, wasInside=${detail.wasInside}, triggered=${detail.triggered}")
            }
            
            // In test mode, trigger for ALL points currently inside (ignore wasInside state)
            val pointsToTrigger = if (settings.testModeEnabled) {
                Log.d(TAG, "TEST MODE: Triggering for all points currently inside, ignoring previous state")
                checkResult.allPointsDetails.filter { it.isInside }.map { it.point }
            } else {
                checkResult.pointsToTrigger
            }

            if (pointsToTrigger.isNotEmpty()) {
                // Filter points that are within their time window
                val activePoints = pointsToTrigger.filter { point ->
                    isWithinTimeWindow(currentHour, currentMinute, point.startHour, point.startMinute, point.endHour, point.endMinute)
                }
                
                if (activePoints.isNotEmpty()) {
                    val modeLabel = if (settings.testModeEnabled) "[TEST MODE] " else ""
                    Log.d(TAG, "${modeLabel}Entered proximity zone for ${activePoints.size} point(s) within time window (current time: $currentHour:${currentMinute.toString().padStart(2, '0')})")
                    activePoints.forEach { point ->
                        Log.d(TAG, "  Point '${point.name}' (${point.startHour}:${point.startMinute.toString().padStart(2, '0')}-${point.endHour}:${point.endMinute.toString().padStart(2, '0')})")
                    }
                    // Use shared alert helper
                    GeofenceHelper.triggerProximityAlert(context, settings.vibrationCount)
                } else {
                    Log.d(TAG, "Entered ${pointsToTrigger.size} proximity zone(s) but none are active at current time: $currentHour:${currentMinute.toString().padStart(2, '0')}")
                }
            } else {
                Log.d(TAG, "No proximity zones entered")
            }

            // Reschedule for next check if interval < 15 minutes
            rescheduleIfNeeded(settings.checkIntervalSeconds)
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during location check", e)
            // Try to reschedule even on error
            try {
                val settings = getSettingsUseCase().first()
                if (settings.isLocationTrackingEnabled) {
                    rescheduleIfNeeded(settings.checkIntervalSeconds)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to reschedule after error", ex)
            }
            return Result.failure()
        }
    }
    
    private suspend fun getCurrentLocation(checkIntervalSeconds: Int): Location? {
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                
                // Try to get last known location first
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null && isLocationRecent(lastLocation, checkIntervalSeconds)) {
                    Log.d(TAG, "Using recent cached location (age: ${(System.currentTimeMillis() - lastLocation.time) / 1000}s, accuracy: ${lastLocation.accuracy}m)")
                    return@withTimeoutOrNull lastLocation
                }
                
                if (lastLocation != null) {
                    Log.d(TAG, "Last location too old (age: ${(System.currentTimeMillis() - lastLocation.time) / 1000}s), requesting fresh location...")
                } else {
                    Log.d(TAG, "No cached location, requesting fresh location...")
                }
                
                // Request fresh location with BALANCED power (works better when phone is in pocket)
                // HIGH_ACCURACY might timeout if GPS can't get a fix quickly
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val freshLocation = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).await()
                    
                    if (freshLocation != null) {
                        Log.d(TAG, "Got fresh location (accuracy: ${freshLocation.accuracy}m)")
                        return@withTimeoutOrNull freshLocation
                    } else {
                        Log.w(TAG, "Fresh location request returned null, using last known location if available")
                        return@withTimeoutOrNull lastLocation // Fallback to last known even if old
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                null
            }
        }
    }
    
    /**
     * Determines if a cached location is recent enough to use.
     * 
     * The threshold is adaptive based on check interval:
     * - For 120s interval: max 60s cache (half of interval)
     * - For 30s interval: max 15s cache (half of interval)
     * - Never more than 60s regardless of interval
     * 
     * This ensures we don't use stale location data that might show us
     * outside a zone when we've already entered it.
     */
    private fun isLocationRecent(location: Location, checkIntervalSeconds: Int): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        // Cache threshold = min(60 seconds, half of check interval)
        // This ensures location is fresher than the check interval would suggest
        val maxAgeMs = minOf(60000L, (checkIntervalSeconds * 1000L) / 2)
        val isRecent = ageMs < maxAgeMs
        Log.d(TAG, "Location age: ${ageMs / 1000}s, max allowed: ${maxAgeMs / 1000}s, recent: $isRecent")
        return isRecent
    }
    
    private fun rescheduleIfNeeded(intervalSeconds: Int) {
        // Only reschedule if interval is less than 15 minutes (WorkManager minimum for PeriodicWork)
        if (intervalSeconds < 900) {
            LocationWorkScheduler(context).scheduleLocationChecks(intervalSeconds)
        }
    }
    
    private fun isWithinTimeWindow(
        currentHour: Int, 
        currentMinute: Int, 
        startHour: Int, 
        startMinute: Int, 
        endHour: Int, 
        endMinute: Int
    ): Boolean {
        // Convert times to minutes since midnight for easier comparison
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute
        
        return if (startTimeInMinutes <= endTimeInMinutes) {
            // Normal case: e.g., 8:30 - 18:45
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } else {
            // Wraps around midnight: e.g., 22:30 - 6:15
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes <= endTimeInMinutes
        }
    }
}
