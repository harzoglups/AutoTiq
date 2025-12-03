package com.fairlaunch.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairlaunch.domain.model.AppSettings
import com.fairlaunch.domain.model.MapLayerType
import com.fairlaunch.domain.model.MapPoint
import com.fairlaunch.domain.usecase.AddMapPointUseCase
import com.fairlaunch.domain.usecase.DeleteMapPointUseCase
import com.fairlaunch.domain.usecase.GetMapPointsUseCase
import com.fairlaunch.domain.usecase.GetSettingsUseCase
import com.fairlaunch.domain.usecase.UpdateMapPointUseCase
import com.fairlaunch.domain.usecase.UpdateSettingsUseCase
import com.fairlaunch.domain.util.Result
import com.fairlaunch.worker.LocationWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getMapPointsUseCase: GetMapPointsUseCase,
    private val addMapPointUseCase: AddMapPointUseCase,
    private val deleteMapPointUseCase: DeleteMapPointUseCase,
    private val updateMapPointUseCase: UpdateMapPointUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val locationWorkScheduler: LocationWorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                getMapPointsUseCase(),
                getSettingsUseCase()
            ) { points, settings ->
                MapUiState.Success(points, settings)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addPoint(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            when (val result = addMapPointUseCase(latitude, longitude)) {
                is Result.Success -> {
                    // Point added successfully, flow will update automatically
                }
                is Result.Error -> {
                    // Handle error if needed
                }
                Result.Loading -> {}
            }
        }
    }

    fun deletePoint(id: Long) {
        viewModelScope.launch {
            deleteMapPointUseCase(id)
        }
    }
    
    fun updatePoint(point: MapPoint) {
        viewModelScope.launch {
            when (updateMapPointUseCase(point)) {
                is Result.Success -> {
                    // Point updated successfully, flow will update automatically
                }
                is Result.Error -> {
                    // Handle error if needed
                }
                Result.Loading -> {}
            }
        }
    }

    fun toggleLocationTracking(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingsUseCase.updateLocationTrackingEnabled(enabled)
            
            if (enabled) {
                // Get current settings to know the interval
                val currentState = _uiState.value
                if (currentState is MapUiState.Success) {
                    locationWorkScheduler.scheduleLocationChecks(
                        currentState.settings.checkIntervalSeconds
                    )
                }
            } else {
                locationWorkScheduler.cancelLocationChecks()
            }
        }
    }

    fun updateMapLayer(layerType: MapLayerType) {
        viewModelScope.launch {
            updateSettingsUseCase.updateMapLayerType(layerType)
        }
    }
}

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(
        val points: List<MapPoint>,
        val settings: AppSettings
    ) : MapUiState
}
