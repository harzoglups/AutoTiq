package com.cussou.autotiq.domain.model

data class AppSettings(
    val checkIntervalSeconds: Int = 300, // 5 minutes = 300 seconds
    val proximityDistanceMeters: Int = 200,
    val isLocationTrackingEnabled: Boolean = false,
    val mapLayerType: MapLayerType = MapLayerType.STREET,
    val activeWeekdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Monday, 7=Sunday (all days by default)
    val vibrationCount: Int = 3, // Number of vibrations when entering a zone (default: 3)
    val themeMode: ThemeMode = ThemeMode.SYSTEM, // Theme preference (default: follow system)
    val testModeEnabled: Boolean = false // Test mode: always trigger notifications even if already inside zone
)

enum class MapLayerType {
    STREET,
    TOPO
}

enum class ThemeMode {
    SYSTEM,  // Follow system theme
    LIGHT,   // Always light theme
    DARK     // Always dark theme
}
