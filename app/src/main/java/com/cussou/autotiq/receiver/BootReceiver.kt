package com.cussou.autotiq.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cussou.autotiq.data.local.AutoTiqDatabase
import com.cussou.autotiq.geofence.GeofenceManager
import com.cussou.autotiq.worker.LocationCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

// DataStore singleton accessor (same pattern as SettingsRepositoryImpl)
private val Context.dataStore by preferencesDataStore(name = "settings")

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, checking if tracking was enabled")
            
            // Use goAsync to allow async operations in BroadcastReceiver
            val pendingResult = goAsync()
            
            scope.launch {
                try {
                    // Check if the datastore file exists to avoid creating a new instance
                    val datastoreFile = File(context.filesDir, "datastore/settings.preferences_pb")
                    
                    if (datastoreFile.exists()) {
                        // Read settings to check if tracking is enabled
                        val dataStore = context.dataStore
                        val preferences = dataStore.data.first()
                        val isTrackingEnabled = preferences[booleanPreferencesKey("location_tracking_enabled")] ?: false
                        val proximityDistance = preferences[intPreferencesKey("proximity_distance_meters")] ?: 200
                        
                        if (isTrackingEnabled) {
                            Log.d("BootReceiver", "Tracking is enabled, re-registering geofences and scheduling worker")
                            
                            // Re-register all geofences (they are cleared on reboot)
                            val database = AutoTiqDatabase.getInstance(context)
                            val mapPointDao = database.mapPointDao()
                            val pointEntities = mapPointDao.getAllPointsOnce()
                            
                            if (pointEntities.isNotEmpty()) {
                                val geofenceManager = GeofenceManager(context)
                                val points = pointEntities.map { entity ->
                                    com.cussou.autotiq.domain.model.MapPoint(
                                        id = entity.id,
                                        latitude = entity.latitude,
                                        longitude = entity.longitude,
                                        name = entity.name,
                                        startHour = entity.startHour,
                                        startMinute = entity.startMinute,
                                        endHour = entity.endHour,
                                        endMinute = entity.endMinute
                                    )
                                }
                                val registered = geofenceManager.registerAllGeofences(
                                    points,
                                    proximityDistance.toFloat()
                                )
                                Log.d("BootReceiver", "Re-registered $registered geofences")
                            } else {
                                Log.d("BootReceiver", "No points to register geofences for")
                            }
                            
                            // Schedule worker with delay to let GPS initialize after boot
                            val workRequest = OneTimeWorkRequestBuilder<LocationCheckWorker>()
                                .setInitialDelay(30, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            
                            WorkManager.getInstance(context).enqueue(workRequest)
                            Log.d("BootReceiver", "Worker scheduled successfully with 30s delay")
                        } else {
                            Log.d("BootReceiver", "Tracking is disabled, not scheduling worker or geofences")
                        }
                    } else {
                        Log.d("BootReceiver", "No settings file found, tracking was never enabled")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error scheduling worker after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
