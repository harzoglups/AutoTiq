package com.fairlaunch.domain.repository

import com.fairlaunch.domain.model.AppSettings
import com.fairlaunch.domain.model.MapLayerType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateCheckInterval(seconds: Int)
    suspend fun updateProximityDistance(meters: Int)
    suspend fun updateLocationTrackingEnabled(enabled: Boolean)
    suspend fun updateMapLayerType(layerType: MapLayerType)
    suspend fun updateActiveWeekdays(weekdays: Set<Int>)
}
