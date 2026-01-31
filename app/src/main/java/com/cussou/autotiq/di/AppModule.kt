package com.cussou.autotiq.di

import android.content.Context
import com.cussou.autotiq.data.local.dao.MapPointDao
import com.cussou.autotiq.data.local.dao.ProximityStateDao
import com.cussou.autotiq.data.repository.MapPointRepositoryImpl
import com.cussou.autotiq.data.repository.ProximityRepositoryImpl
import com.cussou.autotiq.data.repository.SettingsRepositoryImpl
import com.cussou.autotiq.domain.repository.MapPointRepository
import com.cussou.autotiq.domain.repository.ProximityRepository
import com.cussou.autotiq.domain.repository.SettingsRepository
import com.cussou.autotiq.domain.usecase.AddMapPointUseCase
import com.cussou.autotiq.domain.usecase.CheckProximityUseCase
import com.cussou.autotiq.domain.usecase.DeleteMapPointUseCase
import com.cussou.autotiq.domain.usecase.GetMapPointsUseCase
import com.cussou.autotiq.domain.usecase.GetSettingsUseCase
import com.cussou.autotiq.domain.usecase.UpdateMapPointUseCase
import com.cussou.autotiq.domain.usecase.UpdateSettingsUseCase
import com.cussou.autotiq.geofence.GeofenceManager
import com.cussou.autotiq.worker.LocationWorkScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMapPointRepository(dao: MapPointDao): MapPointRepository {
        return MapPointRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideProximityRepository(dao: ProximityStateDao): ProximityRepository {
        return ProximityRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideGetMapPointsUseCase(repository: MapPointRepository): GetMapPointsUseCase {
        return GetMapPointsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideAddMapPointUseCase(repository: MapPointRepository): AddMapPointUseCase {
        return AddMapPointUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideDeleteMapPointUseCase(
        mapPointRepository: MapPointRepository,
        proximityRepository: ProximityRepository
    ): DeleteMapPointUseCase {
        return DeleteMapPointUseCase(mapPointRepository, proximityRepository)
    }

    @Provides
    @Singleton
    fun provideUpdateMapPointUseCase(repository: MapPointRepository): UpdateMapPointUseCase {
        return UpdateMapPointUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetSettingsUseCase(repository: SettingsRepository): GetSettingsUseCase {
        return GetSettingsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideUpdateSettingsUseCase(repository: SettingsRepository): UpdateSettingsUseCase {
        return UpdateSettingsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideCheckProximityUseCase(
        mapPointRepository: MapPointRepository,
        proximityRepository: ProximityRepository
    ): CheckProximityUseCase {
        return CheckProximityUseCase(mapPointRepository, proximityRepository)
    }

    @Provides
    @Singleton
    fun provideLocationWorkScheduler(@ApplicationContext context: Context): LocationWorkScheduler {
        return LocationWorkScheduler(context)
    }

    @Provides
    @Singleton
    fun provideGeofenceManager(@ApplicationContext context: Context): GeofenceManager {
        return GeofenceManager(context)
    }
}
