package com.fairlaunch

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FairLaunchApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        
        // Configure OSMDroid to limit memory usage
        val osmConfig = org.osmdroid.config.Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        
        // Limit disk cache to 50MB (default is 100MB+)
        osmConfig.tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // 50MB disk cache
        osmConfig.tileFileSystemCacheTrimBytes = 40L * 1024 * 1024 // Trim to 40MB when exceeded
        
        // Set expiration for tiles (7 days) to avoid accumulating old data
        osmConfig.expirationExtendedDuration = 7L * 24 * 60 * 60 * 1000 // 7 days
        osmConfig.expirationOverrideDuration = 7L * 24 * 60 * 60 * 1000 // 7 days
        
        // CRITICAL: Limit RAM cache for tiles to prevent high Graphics memory usage
        // Default behavior caches 100+ tiles in RAM (~60-100 MB). We limit to 40 tiles (~20-30 MB).
        osmConfig.cacheMapTileCount = 40.toShort() // Reduced from default (per-zoom level cache)
    }
}
