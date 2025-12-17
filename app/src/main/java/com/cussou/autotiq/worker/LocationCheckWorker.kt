package com.cussou.autotiq.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioAttributes
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cussou.autotiq.R
import com.cussou.autotiq.domain.usecase.CheckProximityUseCase
import com.cussou.autotiq.domain.usecase.GetSettingsUseCase
import com.google.android.gms.location.LocationRequest
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
        private const val FAIRTIQ_PACKAGE = "com.fairtiq.android"
        private const val LOCATION_TIMEOUT_MS = 30000L // 30 seconds timeout for GPS cold start
        private const val NOTIFICATION_CHANNEL_ID = "proximity_alerts"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        Log.d(TAG, "Starting location check...")

        // Check location permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return androidx.work.ListenableWorker.Result.failure()
        }

        try {
            // Get settings first
            val settings = getSettingsUseCase().first()
            Log.d(TAG, "Settings: interval=${settings.checkIntervalSeconds}s, distance=${settings.proximityDistanceMeters}m, enabled=${settings.isLocationTrackingEnabled}")
            
            // If tracking is disabled, don't reschedule
            if (!settings.isLocationTrackingEnabled) {
                Log.d(TAG, "Tracking disabled, stopping worker")
                return androidx.work.ListenableWorker.Result.success()
            }
            
            // Check if today is an active weekday
            val calendar = java.util.Calendar.getInstance()
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            // Convert Calendar day (1=Sunday, 2=Monday, ..., 7=Saturday) to ISO day (1=Monday, ..., 7=Sunday)
            val isoDayOfWeek = if (currentDayOfWeek == java.util.Calendar.SUNDAY) 7 else currentDayOfWeek - 1
            
            if (!settings.activeWeekdays.contains(isoDayOfWeek)) {
                Log.d(TAG, "Today (ISO day $isoDayOfWeek) is not an active day, skipping check. Active days: ${settings.activeWeekdays}")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return androidx.work.ListenableWorker.Result.success()
            }
            
            Log.d(TAG, "Today (ISO day $isoDayOfWeek) is an active day, checking time windows...")
            
            // Get all map points to check time windows
            val allPoints = getMapPointsUseCase().first()
            
            if (allPoints.isEmpty()) {
                Log.d(TAG, "No map points defined, skipping check")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return androidx.work.ListenableWorker.Result.success()
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
                return androidx.work.ListenableWorker.Result.success()
            }
            
            Log.d(TAG, "Current time is within at least one marker's time window, proceeding with GPS scan")
            
            // Get current location with fresh request
            val location = getCurrentLocation()

            if (location == null) {
                Log.w(TAG, "Location is null after timeout, will retry at next interval")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return androidx.work.ListenableWorker.Result.success()
            }

            Log.d(TAG, "Current location: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
            
            // Check proximity - returns list of points we've entered
            val checkResult = checkProximityUseCase.invokeWithDetails(
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                proximityDistanceMeters = settings.proximityDistanceMeters
            )
            
            // Log detailed information about all points
            Log.d(TAG, "Proximity check for ${checkResult.allPointsDetails.size} points (threshold: ${settings.proximityDistanceMeters}m):")
            checkResult.allPointsDetails.forEach { detail ->
                Log.d(TAG, "  '${detail.point.name}': distance=${detail.distance.toInt()}m, isInside=${detail.isInside}, wasInside=${detail.wasInside}, triggered=${detail.triggered}")
            }
            
            val pointsToTrigger = checkResult.pointsToTrigger

            if (pointsToTrigger.isNotEmpty()) {
                // Filter points that are within their time window (currentHour and currentMinute already declared above)
                val activePoints = pointsToTrigger.filter { point ->
                    isWithinTimeWindow(currentHour, currentMinute, point.startHour, point.startMinute, point.endHour, point.endMinute)
                }
                
                if (activePoints.isNotEmpty()) {
                    Log.d(TAG, "Entered proximity zone for ${activePoints.size} point(s) within time window (current time: $currentHour:${currentMinute.toString().padStart(2, '0')})")
                    activePoints.forEach { point ->
                        Log.d(TAG, "  Point '${point.name}' (${point.startHour}:${point.startMinute.toString().padStart(2, '0')}-${point.endHour}:${point.endMinute.toString().padStart(2, '0')})")
                    }
                    launchFairtiqAndVibrate(settings.vibrationCount)
                } else {
                    Log.d(TAG, "Entered ${pointsToTrigger.size} proximity zone(s) but none are active at current time: $currentHour:${currentMinute.toString().padStart(2, '0')}")
                }
            } else {
                Log.d(TAG, "No proximity zones entered")
            }

            // Reschedule for next check if interval < 15 minutes
            rescheduleIfNeeded(settings.checkIntervalSeconds)
            
            return androidx.work.ListenableWorker.Result.success()
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
            return androidx.work.ListenableWorker.Result.failure()
        }
    }
    
    private suspend fun getCurrentLocation(): Location? {
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                
                // Try to get last known location first
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null && isLocationRecent(lastLocation)) {
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
    
    private fun isLocationRecent(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        // Consider location recent if less than 2 minutes old
        // This is reasonable for train station detection
        return ageMs < 120000
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

    private fun launchFairtiqAndVibrate(vibrationCount: Int) {
        try {
            // Vibrate directly FIRST (before notification to ensure it works)
            vibratePhone(vibrationCount)
            
            // Create notification channel
            createNotificationChannel()
            
            // Create intent to launch Fairtiq
            val fairtiqIntent = context.packageManager.getLaunchIntentForPackage(FAIRTIQ_PACKAGE)
            if (fairtiqIntent == null) {
                Log.w(TAG, "Fairtiq app not installed")
                return
            }
            
            fairtiqIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            // Create PendingIntent for full-screen intent
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                fairtiqIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification with full-screen intent (no vibration here, done separately)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Use system icon for now
                .setContentTitle(context.getString(R.string.notification_transport_zone_detected))
                .setContentText(context.getString(R.string.notification_launching_fairtiq))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true) // This launches Fairtiq even with screen off
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification sent with full-screen intent")
            
            // Also try direct launch as fallback (works if screen is on)
            context.startActivity(fairtiqIntent)
            Log.d(TAG, "Direct launch attempted")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Fairtiq or vibrating", e)
        }
    }
    
    private fun vibratePhone(vibrationCount: Int) {
        try {
            Log.d(TAG, "vibratePhone called with count=$vibrationCount")
            
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                if (vibratorManager == null) {
                    Log.e(TAG, "VibratorManager is null")
                    return
                }
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            
            if (vibrator == null) {
                Log.e(TAG, "Vibrator is null")
                return
            }
            
            if (!vibrator.hasVibrator()) {
                Log.e(TAG, "Device has no vibrator")
                return
            }
            
            Log.d(TAG, "Vibrator available, creating pattern for $vibrationCount vibrations")
            
            // Create vibration pattern based on count: vibrate 500ms, pause 200ms, repeat
            val pattern = mutableListOf<Long>()
            val amplitudes = mutableListOf<Int>()
            
            pattern.add(0) // Initial delay
            amplitudes.add(0)
            
            for (i in 0 until vibrationCount) {
                pattern.add(500) // Vibrate 500ms
                amplitudes.add(255) // Max amplitude
                
                if (i < vibrationCount - 1) { // Don't add pause after last vibration
                    pattern.add(200) // Pause 200ms
                    amplitudes.add(0)
                }
            }
            
            Log.d(TAG, "Pattern: ${pattern.toLongArray().contentToString()}, Amplitudes: ${amplitudes.toIntArray().contentToString()}")
            
            // minSdk 26+ guarantees VibrationEffect availability
            // Create AudioAttributes for ALARM usage - this allows vibration from background
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            val effect = VibrationEffect.createWaveform(
                pattern.toLongArray(),
                amplitudes.toIntArray(),
                -1 // Don't repeat
            )
            
            // Note: vibrate(VibrationEffect, AudioAttributes) is deprecated in Android 13+
            // but it's the only way to vibrate from background on Android 12
            // The new API requires VibratorManager which is more complex
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
            Log.d(TAG, "vibrate(effect, audioAttributes) called successfully with USAGE_ALARM")
            
            Log.d(TAG, "Direct vibration triggered ($vibrationCount times)")
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating phone", e)
        }
    }
    
    private fun createNotificationChannel() {
        // minSdk 26+ requires NotificationChannel
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            // Don't set vibration here - we do it manually
            enableVibration(false)
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
