package com.cussou.autotiq.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cussou.autotiq.data.local.AutoTiqDatabase
import com.cussou.autotiq.data.local.entity.ProximityStateEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

/**
 * Receives geofence transition events from Google Play Services.
 * 
 * When the device enters a registered geofence zone, this receiver:
 * 1. Validates the transition (checks settings, time windows, proximity state)
 * 2. Updates proximity state in database to prevent duplicate triggers
 * 3. Triggers the alert (vibration + notification)
 * 
 * This provides near-instant zone detection compared to WorkManager polling.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Geofence transition received")
        
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }
        
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofence error: $errorMessage (code: ${geofencingEvent.errorCode})")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition

        if (transitionType != Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d(TAG, "Ignoring non-ENTER transition: $transitionType")
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences.isNullOrEmpty()) {
            Log.w(TAG, "No triggering geofences")
            return
        }

        Log.d(TAG, "ENTER transition for ${triggeringGeofences.size} geofence(s)")

        // Process asynchronously to not block the main thread
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                processGeofenceTransition(context, triggeringGeofences)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing geofence transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processGeofenceTransition(context: Context, geofences: List<Geofence>) {
        // Read settings directly from DataStore file to avoid dependency injection complexity
        val settings = readSettingsFromDataStore(context)
        
        if (!settings.isTrackingEnabled) {
            Log.d(TAG, "Tracking disabled, ignoring geofence transition")
            return
        }

        // Check if today is an active weekday
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isoDayOfWeek = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1
        
        if (!settings.activeWeekdays.contains(isoDayOfWeek)) {
            Log.d(TAG, "Today (ISO day $isoDayOfWeek) is not an active day, ignoring geofence")
            return
        }

        // Get current time for time window check
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        // Get database to check proximity state and read point details
        val database = AutoTiqDatabase.getInstance(context)
        val proximityDao = database.proximityStateDao()
        val mapPointDao = database.mapPointDao()

        var shouldTrigger = false
        
        for (geofence in geofences) {
            val pointId = geofence.requestId.toLongOrNull()
            if (pointId == null) {
                Log.w(TAG, "Invalid geofence requestId: ${geofence.requestId}")
                continue
            }

            Log.d(TAG, "Processing geofence for point id=$pointId")

            // Get point details for time window check
            val pointEntity = mapPointDao.getPointById(pointId)
            if (pointEntity == null) {
                Log.w(TAG, "Point $pointId not found in database")
                continue
            }

            // Check time window
            if (!isWithinTimeWindow(currentHour, currentMinute, 
                    pointEntity.startHour, pointEntity.startMinute, 
                    pointEntity.endHour, pointEntity.endMinute)) {
                Log.d(TAG, "Point '${pointEntity.name}' outside time window " +
                    "(current: $currentHour:${currentMinute.toString().padStart(2, '0')}, " +
                    "window: ${pointEntity.startHour}:${pointEntity.startMinute.toString().padStart(2, '0')}-" +
                    "${pointEntity.endHour}:${pointEntity.endMinute.toString().padStart(2, '0')})")
                continue
            }

            // Check proximity state to avoid duplicate triggers (unless test mode)
            val previousState = proximityDao.getProximityStateOnce(pointId)
            val wasInside = previousState?.isInside ?: false

            if (settings.testModeEnabled) {
                Log.d(TAG, "TEST MODE: Triggering for '${pointEntity.name}' regardless of previous state")
                shouldTrigger = true
            } else if (!wasInside) {
                Log.d(TAG, "Point '${pointEntity.name}' transition: outside → inside, will trigger")
                shouldTrigger = true
            } else {
                Log.d(TAG, "Point '${pointEntity.name}' was already inside, skipping (wasInside=$wasInside)")
            }

            // Update proximity state
            proximityDao.insertProximityState(
                ProximityStateEntity(
                    pointId = pointId,
                    isInside = true,
                    lastChecked = System.currentTimeMillis()
                )
            )
        }

        if (shouldTrigger) {
            Log.d(TAG, "Triggering proximity alert from geofence")
            GeofenceHelper.triggerProximityAlert(context, settings.vibrationCount)
        } else {
            Log.d(TAG, "No alert triggered (all points filtered by time window or state)")
        }
    }

    /**
     * Simple settings reader that reads directly from DataStore file.
     * This avoids the complexity of injecting repositories into a BroadcastReceiver.
     * 
     * Note: We use a timeout to avoid hanging if DataStore is locked by another process.
     */
    private suspend fun readSettingsFromDataStore(context: Context): GeofenceSettings {
        return try {
            // Use withTimeout to avoid hanging if DataStore is locked
            kotlinx.coroutines.withTimeoutOrNull(2000L) {
                val dataStore = context.dataStore
                val preferences = dataStore.data.first()
                
                val isTrackingEnabled = preferences[booleanPreferencesKey("location_tracking_enabled")] ?: false
                val vibrationCount = preferences[intPreferencesKey("vibration_count")] ?: 3
                val testModeEnabled = preferences[booleanPreferencesKey("test_mode_enabled")] ?: false
                val weekdaysString = preferences[stringPreferencesKey("active_weekdays")] 
                    ?: "1,2,3,4,5,6,7" // Default: all days
                val activeWeekdays = try {
                    weekdaysString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                } catch (e: Exception) {
                    setOf(1, 2, 3, 4, 5, 6, 7)
                }
                
                Log.d(TAG, "Read settings: tracking=$isTrackingEnabled, vibrations=$vibrationCount, testMode=$testModeEnabled")
                
                GeofenceSettings(
                    isTrackingEnabled = isTrackingEnabled,
                    vibrationCount = vibrationCount,
                    testModeEnabled = testModeEnabled,
                    activeWeekdays = activeWeekdays
                )
            } ?: run {
                // Timeout - DataStore might be locked, assume tracking is enabled
                // (better to trigger than to miss a notification)
                Log.w(TAG, "DataStore read timeout, assuming tracking is enabled")
                GeofenceSettings(
                    isTrackingEnabled = true,
                    vibrationCount = 3,
                    testModeEnabled = false,
                    activeWeekdays = setOf(1, 2, 3, 4, 5, 6, 7)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading settings from DataStore: ${e.message}", e)
            // On error, assume tracking is enabled (better to trigger than to miss)
            // The WorkManager will also check and won't double-trigger due to proximity state
            GeofenceSettings(
                isTrackingEnabled = true,
                vibrationCount = 3,
                testModeEnabled = false,
                activeWeekdays = setOf(1, 2, 3, 4, 5, 6, 7)
            )
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
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute
        
        return if (startTimeInMinutes <= endTimeInMinutes) {
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } else {
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes <= endTimeInMinutes
        }
    }

    /**
     * Simple data class for settings needed by geofence processing.
     */
    private data class GeofenceSettings(
        val isTrackingEnabled: Boolean,
        val vibrationCount: Int,
        val testModeEnabled: Boolean,
        val activeWeekdays: Set<Int>
    )
}

// DataStore singleton accessor (same pattern as SettingsRepositoryImpl)
private val Context.dataStore by androidx.datastore.preferences.preferencesDataStore(name = "settings")
