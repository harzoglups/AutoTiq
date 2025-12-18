package com.cussou.autotiq.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cussou.autotiq.domain.model.AppSettings
import com.cussou.autotiq.domain.model.MapLayerType
import com.cussou.autotiq.domain.model.ThemeMode
import com.cussou.autotiq.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl @Inject constructor(
    private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val CHECK_INTERVAL = intPreferencesKey("check_interval_seconds")
        val PROXIMITY_DISTANCE = intPreferencesKey("proximity_distance_meters")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val MAP_LAYER_TYPE = stringPreferencesKey("map_layer_type")
        val ACTIVE_WEEKDAYS = stringPreferencesKey("active_weekdays")
        val VIBRATION_COUNT = intPreferencesKey("vibration_count")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEST_MODE_ENABLED = booleanPreferencesKey("test_mode_enabled")
    }

    override fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { preferences ->
            val layerTypeString = preferences[PreferencesKeys.MAP_LAYER_TYPE] ?: MapLayerType.STREET.name
            val layerType = try {
                MapLayerType.valueOf(layerTypeString)
            } catch (e: IllegalArgumentException) {
                MapLayerType.STREET
            }
            
            val weekdaysString = preferences[PreferencesKeys.ACTIVE_WEEKDAYS] ?: "1,2,3,4,5,6,7"
            val activeWeekdays = try {
                weekdaysString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            } catch (e: Exception) {
                setOf(1, 2, 3, 4, 5, 6, 7)
            }
            
            val themeModeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val themeMode = try {
                ThemeMode.valueOf(themeModeString)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
            
            AppSettings(
                checkIntervalSeconds = preferences[PreferencesKeys.CHECK_INTERVAL] ?: 300,
                proximityDistanceMeters = preferences[PreferencesKeys.PROXIMITY_DISTANCE] ?: 200,
                isLocationTrackingEnabled = preferences[PreferencesKeys.LOCATION_TRACKING_ENABLED] ?: false,
                mapLayerType = layerType,
                activeWeekdays = activeWeekdays,
                vibrationCount = preferences[PreferencesKeys.VIBRATION_COUNT] ?: 3,
                themeMode = themeMode,
                testModeEnabled = preferences[PreferencesKeys.TEST_MODE_ENABLED] ?: false
            )
        }
    }

    override suspend fun updateCheckInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHECK_INTERVAL] = seconds
        }
    }

    override suspend fun updateProximityDistance(meters: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROXIMITY_DISTANCE] = meters
        }
    }

    override suspend fun updateLocationTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCATION_TRACKING_ENABLED] = enabled
        }
    }

    override suspend fun updateMapLayerType(layerType: MapLayerType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAP_LAYER_TYPE] = layerType.name
        }
    }
    
    override suspend fun updateActiveWeekdays(weekdays: Set<Int>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_WEEKDAYS] = weekdays.sorted().joinToString(",")
        }
    }
    
    override suspend fun updateVibrationCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_COUNT] = count
        }
    }
    
    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }
    
    override suspend fun updateTestModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEST_MODE_ENABLED] = enabled
        }
    }
}
