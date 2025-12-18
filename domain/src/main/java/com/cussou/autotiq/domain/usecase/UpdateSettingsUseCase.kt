package com.cussou.autotiq.domain.usecase

import com.cussou.autotiq.domain.model.MapLayerType
import com.cussou.autotiq.domain.model.ThemeMode
import com.cussou.autotiq.domain.repository.SettingsRepository

class UpdateSettingsUseCase(
    private val repository: SettingsRepository
) {
    suspend fun updateCheckInterval(seconds: Int) {
        repository.updateCheckInterval(seconds)
    }

    suspend fun updateProximityDistance(meters: Int) {
        repository.updateProximityDistance(meters)
    }

    suspend fun updateLocationTrackingEnabled(enabled: Boolean) {
        repository.updateLocationTrackingEnabled(enabled)
    }

    suspend fun updateMapLayerType(layerType: MapLayerType) {
        repository.updateMapLayerType(layerType)
    }
    
    suspend fun updateActiveWeekdays(weekdays: Set<Int>) {
        repository.updateActiveWeekdays(weekdays)
    }
    
    suspend fun updateVibrationCount(count: Int) {
        repository.updateVibrationCount(count)
    }
    
    suspend fun updateThemeMode(themeMode: ThemeMode) {
        repository.updateThemeMode(themeMode)
    }
}
