package com.cussou.autotiq.geofence

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cussou.autotiq.R

/**
 * Shared helper for triggering proximity alerts (vibration + notification + Fairtiq launch).
 * Used by both LocationCheckWorker and GeofenceBroadcastReceiver to ensure consistent behavior.
 */
object GeofenceHelper {

    private const val TAG = "GeofenceHelper"
    const val FAIRTIQ_PACKAGE = "com.fairtiq.android"
    const val NOTIFICATION_CHANNEL_ID = "proximity_alerts"
    const val NOTIFICATION_ID = 1001
    
    // Separate channel for expedited worker foreground notification (low priority, silent)
    const val EXPEDITED_CHANNEL_ID = "expedited_work"
    const val EXPEDITED_NOTIFICATION_ID = 1002

    /**
     * Triggers the full proximity alert sequence:
     * 1. Vibrates the phone
     * 2. Shows notification with Fairtiq launch intent
     * 3. Attempts direct Fairtiq launch if app is in foreground
     */
    fun triggerProximityAlert(context: Context, vibrationCount: Int) {
        try {
            // Vibrate directly FIRST (before notification to ensure it works)
            vibratePhone(context, vibrationCount)
            
            // Check if our app is in foreground
            val isAppInForeground = isAppInForeground(context)
            Log.d(TAG, "App in foreground: $isAppInForeground")
            
            // Create notification channel
            createNotificationChannel(context)
            
            // Create intent to launch Fairtiq
            val fairtiqIntent = context.packageManager.getLaunchIntentForPackage(FAIRTIQ_PACKAGE)
            if (fairtiqIntent == null) {
                Log.w(TAG, "Fairtiq app not installed")
                return
            }
            
            fairtiqIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            // If app is in foreground, try direct launch (will work!)
            if (isAppInForeground) {
                try {
                    context.startActivity(fairtiqIntent)
                    Log.d(TAG, "Direct Fairtiq launch attempted (app was in foreground)")
                } catch (e: Exception) {
                    Log.e(TAG, "Direct launch failed even though app in foreground", e)
                }
            }
            
            // Create PendingIntent for full-screen intent
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                fairtiqIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if we have permission to use full-screen intents (Android 14+)
            val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent()
            } else {
                true // Automatically granted on Android 13 and below
            }
            
            Log.d(TAG, "Full-screen intent permission: $canUseFullScreenIntent")
            
            // Build notification with full-screen intent (no vibration here, done separately)
            val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.notification_transport_zone_detected))
                .setContentText(context.getString(R.string.notification_tap_to_launch_fairtiq))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)
            
            // Only use full-screen intent if we have permission
            if (canUseFullScreenIntent) {
                notificationBuilder.setFullScreenIntent(pendingIntent, true)
                Log.d(TAG, "Full-screen intent added to notification (will auto-launch Fairtiq when screen is locked)")
            } else {
                Log.w(TAG, "Cannot use full-screen intent, user needs to grant permission in system settings")
            }
            
            val notification = notificationBuilder.build()
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification sent (user must tap it to launch Fairtiq when screen is unlocked)")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering proximity alert", e)
        }
    }

    /**
     * Vibrates the phone with the specified pattern count.
     */
    fun vibratePhone(context: Context, vibrationCount: Int) {
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
            
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
            Log.d(TAG, "vibrate(effect, audioAttributes) called successfully with USAGE_ALARM")
            
            Log.d(TAG, "Direct vibration triggered ($vibrationCount times)")
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating phone", e)
        }
    }

    /**
     * Creates the notification channel for proximity alerts.
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(false) // We handle vibration manually
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates a low-priority notification channel for expedited worker foreground notification.
     * This is required by WorkManager for expedited work on Android < 12.
     */
    fun createExpeditedChannel(context: Context) {
        val channel = NotificationChannel(
            EXPEDITED_CHANNEL_ID,
            "Background location check",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when checking your location in the background"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Checks if the app is currently in the foreground.
     */
    fun isAppInForeground(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: return false
            
            runningProcesses.any { processInfo ->
                processInfo.processName == context.packageName && 
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is in foreground", e)
            false
        }
    }
}
