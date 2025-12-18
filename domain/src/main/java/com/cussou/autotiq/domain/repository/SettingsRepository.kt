package com.cussou.autotiq.domain.repository

import com.cussou.autotiq.domain.model.AppSettings
import com.cussou.autotiq.domain.model.MapLayerType
import com.cussou.autotiq.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateCheckInterval(seconds: Int)
    suspend fun updateProximityDistance(meters: Int)
    suspend fun updateLocationTrackingEnabled(enabled: Boolean)
    suspend fun updateMapLayerType(layerType: MapLayerType)
    suspend fun updateActiveWeekdays(weekdays: Set<Int>)
    suspend fun updateVibrationCount(count: Int)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateTestModeEnabled(enabled: Boolean)
}
