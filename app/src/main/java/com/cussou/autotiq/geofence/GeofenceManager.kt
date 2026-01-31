package com.cussou.autotiq.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.cussou.autotiq.domain.model.MapPoint
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * Manages geofence registration and unregistration with Google Play Services.
 * 
 * Geofences provide event-driven zone detection that triggers immediately when
 * crossing a boundary, unlike polling-based WorkManager checks that can be delayed.
 * 
 * Key behaviors:
 * - Geofences survive app restarts but are cleared on device reboot
 * - Maximum 100 geofences per app (unlikely to hit with train stations)
 * - Minimum reliable radius is ~100m; smaller radii may be unreliable
 * - Trigger delay is typically 30s-3min, much faster than delayed WorkManager
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        
        // Action for the geofence broadcast receiver
        const val GEOFENCE_ACTION = "com.cussou.autotiq.GEOFENCE_TRANSITION"
        
        // Request code for the pending intent
        private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 1001
        
        // Geofence expiration: never expire (we manage lifecycle manually)
        private const val GEOFENCE_EXPIRATION_MS = Geofence.NEVER_EXPIRE
        
        // Loitering delay for DWELL transitions (not currently used, but available)
        private const val GEOFENCE_LOITERING_DELAY_MS = 0
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    
    // Cached pending intent for geofence transitions
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(GEOFENCE_ACTION).apply {
            setPackage(context.packageName)
            setClass(context, GeofenceBroadcastReceiver::class.java)
        }
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Registers a geofence for a single map point.
     * 
     * @param point The map point to create a geofence for
     * @param radiusMeters The radius of the geofence in meters
     * @return true if registration was successful, false otherwise
     */
    suspend fun registerGeofence(point: MapPoint, radiusMeters: Float): Boolean {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Cannot register geofence: location permission not granted")
            return false
        }

        val geofence = buildGeofence(point, radiusMeters)
        val request = buildGeofenceRequest(listOf(geofence))

        return try {
            geofencingClient.addGeofences(request, geofencePendingIntent).await()
            Log.d(TAG, "Registered geofence for point '${point.name}' (id=${point.id}, radius=${radiusMeters}m)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception registering geofence for '${point.name}'", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error registering geofence for '${point.name}'", e)
            false
        }
    }

    /**
     * Registers geofences for multiple map points.
     * More efficient than registering individually.
     * 
     * @param points List of map points to create geofences for
     * @param radiusMeters The radius of each geofence in meters
     * @return Number of successfully registered geofences
     */
    suspend fun registerAllGeofences(points: List<MapPoint>, radiusMeters: Float): Int {
        if (points.isEmpty()) {
            Log.d(TAG, "No points to register geofences for")
            return 0
        }

        if (!hasLocationPermission()) {
            Log.e(TAG, "Cannot register geofences: location permission not granted")
            return 0
        }

        // Android has a limit of ~100 geofences per app
        if (points.size > 100) {
            Log.w(TAG, "Too many points (${points.size}), only first 100 will have geofences")
        }

        val geofences = points.take(100).map { buildGeofence(it, radiusMeters) }
        val request = buildGeofenceRequest(geofences)

        return try {
            geofencingClient.addGeofences(request, geofencePendingIntent).await()
            Log.d(TAG, "Registered ${geofences.size} geofences (radius=${radiusMeters}m)")
            geofences.size
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception registering geofences", e)
            0
        } catch (e: Exception) {
            Log.e(TAG, "Error registering geofences", e)
            0
        }
    }

    /**
     * Unregisters a geofence for a specific point ID.
     * 
     * @param pointId The ID of the point to unregister
     * @return true if unregistration was successful, false otherwise
     */
    suspend fun unregisterGeofence(pointId: Long): Boolean {
        return try {
            geofencingClient.removeGeofences(listOf(pointId.toString())).await()
            Log.d(TAG, "Unregistered geofence for point id=$pointId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering geofence for point id=$pointId", e)
            false
        }
    }

    /**
     * Unregisters all geofences managed by this app.
     * 
     * @return true if unregistration was successful, false otherwise
     */
    suspend fun unregisterAllGeofences(): Boolean {
        return try {
            geofencingClient.removeGeofences(geofencePendingIntent).await()
            Log.d(TAG, "Unregistered all geofences")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering all geofences", e)
            false
        }
    }

    /**
     * Builds a Geofence object from a MapPoint.
     */
    private fun buildGeofence(point: MapPoint, radiusMeters: Float): Geofence {
        return Geofence.Builder()
            .setRequestId(point.id.toString())
            .setCircularRegion(
                point.latitude,
                point.longitude,
                radiusMeters
            )
            .setExpirationDuration(GEOFENCE_EXPIRATION_MS)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setLoiteringDelay(GEOFENCE_LOITERING_DELAY_MS)
            .build()
    }

    /**
     * Builds a GeofencingRequest from a list of Geofences.
     */
    private fun buildGeofenceRequest(geofences: List<Geofence>): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
    }

    /**
     * Checks if location permission is granted.
     */
    private fun hasLocationPermission(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation && backgroundLocation
    }
}
