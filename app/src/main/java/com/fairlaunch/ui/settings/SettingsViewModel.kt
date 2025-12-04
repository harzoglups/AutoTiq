package com.fairlaunch.ui.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairlaunch.domain.model.AppSettings
import com.fairlaunch.domain.usecase.GetSettingsUseCase
import com.fairlaunch.domain.usecase.UpdateSettingsUseCase
import com.fairlaunch.worker.LocationWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PermissionStatus {
    NOT_GRANTED,
    FOREGROUND_ONLY,
    GRANTED
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val locationWorkScheduler: LocationWorkScheduler
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    val settings: StateFlow<AppSettings> = getSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )
    
    private val _permissionStatus = MutableStateFlow(checkPermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    init {
        Log.d(TAG, "SettingsViewModel initialized")
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "Initial permission status: ${_permissionStatus.value}")
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
}
