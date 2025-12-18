package com.cussou.autotiq.ui.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cussou.autotiq.domain.model.AppSettings
import com.cussou.autotiq.domain.usecase.GetSettingsUseCase
import com.cussou.autotiq.domain.usecase.UpdateSettingsUseCase
import com.cussou.autotiq.worker.LocationWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class PermissionStatus {
    NOT_GRANTED,
    FOREGROUND_ONLY,
    GRANTED
}

sealed class ImportExportEvent {
    data class ShowToast(val messageResId: Int, val args: Array<Any> = emptyArray()) : ImportExportEvent()
    data class ShowImportStrategyDialog(
        val zonesToImport: List<ZoneExport>,
        val existingZonesCount: Int
    ) : ImportExportEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val locationWorkScheduler: LocationWorkScheduler,
    private val getMapPointsUseCase: com.cussou.autotiq.domain.usecase.GetMapPointsUseCase,
    private val mapPointRepository: com.cussou.autotiq.domain.repository.MapPointRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val FAIRTIQ_PACKAGE = "com.fairtiq.android"
    }

    val settings: StateFlow<AppSettings> = getSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )
    
    private val _permissionStatus = MutableStateFlow(checkPermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()
    
    private val _importExportEvent = MutableStateFlow<ImportExportEvent?>(null)
    val importExportEvent: StateFlow<ImportExportEvent?> = _importExportEvent.asStateFlow()
    
    private val _isFairtiqInstalled = MutableStateFlow(checkFairtiqInstalled())
    val isFairtiqInstalled: StateFlow<Boolean> = _isFairtiqInstalled.asStateFlow()

    init {
        Log.d(TAG, "SettingsViewModel initialized")
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "Initial permission status: ${_permissionStatus.value}")
        Log.d(TAG, "Fairtiq installed: ${_isFairtiqInstalled.value}")
    }
    
    private fun checkFairtiqInstalled(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            application.packageManager.getPackageInfo(FAIRTIQ_PACKAGE, 0)
            Log.d(TAG, "Fairtiq app is installed")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Fairtiq app is NOT installed")
            false
        }
    }

    private fun checkPermissionStatus(): PermissionStatus {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Has FINE_LOCATION: $hasFineLocation")
        
        if (!hasFineLocation) {
            return PermissionStatus.NOT_GRANTED
        }
        
        // Check background location for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "Has BACKGROUND_LOCATION: $hasBackgroundLocation")
            
            return if (hasBackgroundLocation) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.FOREGROUND_ONLY
            }
        }
        
        // On Android 9 and below, fine location is sufficient
        Log.d(TAG, "Android < 10, returning GRANTED")
        return PermissionStatus.GRANTED
    }
    
    fun refreshPermissionStatus() {
        Log.d(TAG, "Refreshing permission status")
        _permissionStatus.value = checkPermissionStatus()
        Log.d(TAG, "New permission status: ${_permissionStatus.value}")
    }
    
    fun onBackgroundLocationPermissionResult(isGranted: Boolean) {
        Log.d(TAG, "Background location permission result: $isGranted")
        refreshPermissionStatus()
        // If permission was just granted and tracking should be enabled, enable it
        if (isGranted && _permissionStatus.value == PermissionStatus.GRANTED) {
            updateLocationTracking(true)
        }
    }

    fun updateCheckInterval(seconds: Int) {
        viewModelScope.launch {
            updateSettingsUseCase.updateCheckInterval(seconds)
            // If tracking is enabled, reschedule with new interval
            if (settings.value.isLocationTrackingEnabled) {
                locationWorkScheduler.scheduleLocationChecks(seconds)
            }
        }
    }

    fun updateProximityDistance(meters: Int) {
        viewModelScope.launch {
            updateSettingsUseCase.updateProximityDistance(meters)
        }
    }

    fun updateLocationTracking(enabled: Boolean) {
        Log.d(TAG, "Updating location tracking: $enabled")
        viewModelScope.launch {
            updateSettingsUseCase.updateLocationTrackingEnabled(enabled)
            
            if (enabled) {
                locationWorkScheduler.scheduleLocationChecks(settings.value.checkIntervalSeconds)
            } else {
                locationWorkScheduler.cancelLocationChecks()
            }
        }
    }
    
    fun updateActiveWeekdays(weekdays: Set<Int>) {
        viewModelScope.launch {
            updateSettingsUseCase.updateActiveWeekdays(weekdays)
        }
    }
    
    fun updateVibrationCount(count: Int) {
        viewModelScope.launch {
            updateSettingsUseCase.updateVibrationCount(count)
        }
    }
    
    fun updateThemeMode(themeMode: com.cussou.autotiq.domain.model.ThemeMode) {
        viewModelScope.launch {
            updateSettingsUseCase.updateThemeMode(themeMode)
        }
    }
    
    fun updateTestMode(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingsUseCase.updateTestMode(enabled)
        }
    }
    
    fun clearEvent() {
        _importExportEvent.value = null
    }
    
    /**
     * Initiates the export process by requesting file creation
     * The actual export happens in performExport() after the user selects a file
     */
    fun exportZones() {
        // This function is now just a placeholder, the actual export is triggered
        // by the ActivityResultLauncher in SettingsScreen
        Log.d(TAG, "Export zones launcher will be triggered")
    }
    
    /**
     * Performs the actual export to the specified URI
     */
    fun performExport(uri: Uri) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting export to $uri")
                
                // Get all zones
                val mapPoints = getMapPointsUseCase().first()
                
                if (mapPoints.isEmpty()) {
                    Log.w(TAG, "No zones to export")
                    _importExportEvent.value = ImportExportEvent.ShowToast(
                        com.cussou.autotiq.R.string.no_zones_to_export
                    )
                    return@launch
                }
                
                // Build JSON manually
                val zonesArray = JSONArray()
                mapPoints.forEach { point ->
                    val zoneObj = JSONObject().apply {
                        put("name", point.name)
                        put("latitude", point.latitude)
                        put("longitude", point.longitude)
                        put("startHour", point.startHour)
                        put("startMinute", point.startMinute)
                        put("endHour", point.endHour)
                        put("endMinute", point.endMinute)
                    }
                    zonesArray.put(zoneObj)
                }
                
                val exportData = JSONObject().apply {
                    put("version", 1)
                    put("exportDate", Instant.now()
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT))
                    put("zones", zonesArray)
                }
                
                // Write to file
                application.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportData.toString(2).toByteArray())
                    outputStream.flush()
                }
                
                Log.d(TAG, "Export successful: ${mapPoints.size} zones")
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.export_success
                )
            } catch (e: IOException) {
                Log.e(TAG, "IO error exporting zones", e)
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.export_error
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting zones", e)
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.export_error
                )
            }
        }
    }
    
    /**
     * Initiates the import process by requesting file selection
     * The actual import happens in performImport() after the user selects a file
     */
    fun importZones() {
        // This function is now just a placeholder, the actual import is triggered
        // by the ActivityResultLauncher in SettingsScreen
        Log.d(TAG, "Import zones launcher will be triggered")
    }
    
    /**
     * Performs the actual import from the specified URI
     * Shows a dialog asking user to replace or merge if zones already exist
     */
    fun performImport(uri: Uri) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting import from $uri")
                
                // Read and parse file
                val jsonString = application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                } ?: run {
                    Log.e(TAG, "Failed to read file")
                    _importExportEvent.value = ImportExportEvent.ShowToast(
                        com.cussou.autotiq.R.string.import_error
                    )
                    return@launch
                }
                
                // Parse JSON
                val importData = try {
                    JSONObject(jsonString)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid JSON format", e)
                    _importExportEvent.value = ImportExportEvent.ShowToast(
                        com.cussou.autotiq.R.string.import_error
                    )
                    return@launch
                }
                
                // Extract zones array
                val zonesArray = try {
                    importData.getJSONArray("zones")
                } catch (e: Exception) {
                    Log.e(TAG, "No zones array found in JSON", e)
                    _importExportEvent.value = ImportExportEvent.ShowToast(
                        com.cussou.autotiq.R.string.import_error
                    )
                    return@launch
                }
                
                if (zonesArray.length() == 0) {
                    Log.w(TAG, "No zones found in import file")
                    _importExportEvent.value = ImportExportEvent.ShowToast(
                        com.cussou.autotiq.R.string.import_error
                    )
                    return@launch
                }
                
                // Parse and validate each zone
                val validZones = mutableListOf<ZoneExport>()
                for (i in 0 until zonesArray.length()) {
                    try {
                        val zoneObj = zonesArray.getJSONObject(i)
                        val zone = ZoneExport(
                            name = zoneObj.optString("name", ""),
                            latitude = zoneObj.getDouble("latitude"),
                            longitude = zoneObj.getDouble("longitude"),
                            startHour = zoneObj.getInt("startHour"),
                            startMinute = zoneObj.getInt("startMinute"),
                            endHour = zoneObj.getInt("endHour"),
                            endMinute = zoneObj.getInt("endMinute")
                        )
                        
                        // Validate zone
                        if (zone.latitude in -90.0..90.0 &&
                            zone.longitude in -180.0..180.0 &&
                            zone.startHour in 0..23 &&
                            zone.startMinute in 0..59 &&
                            zone.endHour in 0..23 &&
                            zone.endMinute in 0..59
                        ) {
                            validZones.add(zone)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping invalid zone at index $i", e)
                    }
                }
                
                if (validZones.isEmpty()) {
                    Log.w(TAG, "No valid zones found in import file")
                    _importExportEvent.value = ImportExportEvent.ShowToast(
                        com.cussou.autotiq.R.string.import_error
                    )
                    return@launch
                }
                
                Log.d(TAG, "Found ${validZones.size} valid zones")
                
                // Check if existing zones exist
                val existingZones = getMapPointsUseCase().first()
                
                if (existingZones.isEmpty()) {
                    // No existing zones, import directly
                    Log.d(TAG, "No existing zones, importing directly")
                    importZonesDirectly(validZones)
                } else {
                    // Show dialog to choose import strategy
                    Log.d(TAG, "Found ${existingZones.size} existing zones, showing strategy dialog")
                    _importExportEvent.value = ImportExportEvent.ShowImportStrategyDialog(
                        zonesToImport = validZones,
                        existingZonesCount = existingZones.size
                    )
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "IO error importing zones", e)
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.import_error
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error importing zones", e)
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.import_error
                )
            }
        }
    }
    
    private suspend fun importZonesDirectly(zones: List<ZoneExport>) {
        try {
            var successCount = 0
            zones.forEach { zone ->
                val result = mapPointRepository.insertPoint(zone.toMapPoint())
                if (result is com.cussou.autotiq.domain.util.Result.Success) {
                    successCount++
                }
            }
            
            Log.d(TAG, "Import successful: $successCount zones")
            _importExportEvent.value = ImportExportEvent.ShowToast(
                com.cussou.autotiq.R.string.import_success,
                arrayOf(successCount)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adding imported zones", e)
            _importExportEvent.value = ImportExportEvent.ShowToast(
                com.cussou.autotiq.R.string.import_error
            )
        }
    }
    
    /**
     * Import zones with REPLACE strategy: delete all existing zones first, then import
     */
    fun importWithReplace(zones: List<ZoneExport>) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Import with REPLACE strategy: deleting all existing zones")
                
                // Get all existing zones
                val existingZones = getMapPointsUseCase().first()
                
                // Delete all existing zones
                existingZones.forEach { point ->
                    mapPointRepository.deletePoint(point.id)
                }
                
                Log.d(TAG, "Deleted ${existingZones.size} existing zones, now importing ${zones.size} new zones")
                
                // Import new zones
                var successCount = 0
                zones.forEach { zone ->
                    val result = mapPointRepository.insertPoint(zone.toMapPoint())
                    if (result is com.cussou.autotiq.domain.util.Result.Success) {
                        successCount++
                    }
                }
                
                Log.d(TAG, "Replace import successful: $successCount zones")
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.import_replace_success,
                    arrayOf(successCount)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during replace import", e)
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.import_error
                )
            }
        }
    }
    
    /**
     * Import zones with MERGE strategy: skip duplicates (exact lat/lon match)
     */
    fun importWithMerge(zones: List<ZoneExport>) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Import with MERGE strategy: skipping duplicates")
                
                // Get all existing zones
                val existingZones = getMapPointsUseCase().first()
                
                // Create a set of existing coordinates for fast lookup
                val existingCoordinates = existingZones.map { 
                    Pair(it.latitude, it.longitude) 
                }.toSet()
                
                Log.d(TAG, "Found ${existingCoordinates.size} existing coordinate pairs")
                
                // Filter out zones that already exist (exact lat/lon match)
                val newZones = zones.filter { zone ->
                    val coordinates = Pair(zone.latitude, zone.longitude)
                    !existingCoordinates.contains(coordinates)
                }
                
                val skippedCount = zones.size - newZones.size
                Log.d(TAG, "Skipping $skippedCount duplicate zones, importing ${newZones.size} new zones")
                
                // Import only new zones
                var successCount = 0
                newZones.forEach { zone ->
                    val result = mapPointRepository.insertPoint(zone.toMapPoint())
                    if (result is com.cussou.autotiq.domain.util.Result.Success) {
                        successCount++
                    }
                }
                
                Log.d(TAG, "Merge import successful: $successCount new zones, $skippedCount duplicates skipped")
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.import_merge_success,
                    arrayOf(successCount, skippedCount)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during merge import", e)
                _importExportEvent.value = ImportExportEvent.ShowToast(
                    com.cussou.autotiq.R.string.import_error
                )
            }
        }
    }
}
