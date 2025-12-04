package com.fairlaunch.worker

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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fairlaunch.R
import com.fairlaunch.domain.usecase.CheckProximityUseCase
import com.fairlaunch.domain.usecase.GetSettingsUseCase
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
    private val getSettingsUseCase: GetSettingsUseCase
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
            
            Log.d(TAG, "Today (ISO day $isoDayOfWeek) is an active day, proceeding with location check")
            
            // Get current location with fresh request
            val location = getCurrentLocation()

            if (location == null) {
                Log.w(TAG, "Location is null after timeout, will retry at next interval")
                rescheduleIfNeeded(settings.checkIntervalSeconds)
                return androidx.work.ListenableWorker.Result.success()
            }

            Log.d(TAG, "Current location: ${location.latitude}, ${location.longitude}")
            
            // Check proximity - returns list of points we've entered
            val pointsToTrigger = checkProximityUseCase(
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                proximityDistanceMeters = settings.proximityDistanceMeters
            )

            if (pointsToTrigger.isNotEmpty()) {
                // Get current hour and minute
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val currentMinute = calendar.get(java.util.Calendar.MINUTE)
                
                // Filter points that are within their time window
                val activePoints = pointsToTrigger.filter { point ->
                    isWithinTimeWindow(currentHour, currentMinute, point.startHour, point.startMinute, point.endHour, point.endMinute)
                }
                
                if (activePoints.isNotEmpty()) {
                    Log.d(TAG, "Entered proximity zone for ${activePoints.size} point(s) within time window (current time: $currentHour:${currentMinute.toString().padStart(2, '0')})")
                    activePoints.forEach { point ->
                        Log.d(TAG, "  Point '${point.name}' (${point.startHour}:${point.startMinute.toString().padStart(2, '0')}-${point.endHour}:${point.endMinute.toString().padStart(2, '0')})")
                    }
                    launchFairtiqAndVibrate()
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
                    Log.d(TAG, "Using recent cached location")
                    return@withTimeoutOrNull lastLocation
                }
                
                // Request fresh location
                Log.d(TAG, "Requesting fresh location...")
                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    5000L
                ).apply {
                    setMaxUpdates(1)
                    setWaitForAccurateLocation(false)
                }.build()
                
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).await()
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
        return ageMs < 60000 // Less than 1 minute old
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

    private fun launchFairtiqAndVibrate() {
        try {
            // Vibrate directly FIRST (before notification to ensure it works)
            vibratePhone()
            
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
    
    private fun vibratePhone() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            // Strong vibration pattern: 3 long bursts
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    pattern,
                    intArrayOf(0, 255, 0, 255, 0, 255), // Max amplitude
                    -1 // Don't repeat
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            
            Log.d(TAG, "Direct vibration triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating phone", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}
