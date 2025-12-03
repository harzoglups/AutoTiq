package com.fairlaunch.di

import android.content.Context
import com.fairlaunch.data.local.dao.MapPointDao
import com.fairlaunch.data.local.dao.ProximityStateDao
import com.fairlaunch.data.repository.MapPointRepositoryImpl
import com.fairlaunch.data.repository.ProximityRepositoryImpl
import com.fairlaunch.data.repository.SettingsRepositoryImpl
import com.fairlaunch.domain.repository.MapPointRepository
import com.fairlaunch.domain.repository.ProximityRepository
import com.fairlaunch.domain.repository.SettingsRepository
import com.fairlaunch.domain.usecase.AddMapPointUseCase
import com.fairlaunch.domain.usecase.CheckProximityUseCase
import com.fairlaunch.domain.usecase.DeleteMapPointUseCase
import com.fairlaunch.domain.usecase.GetMapPointsUseCase
import com.fairlaunch.domain.usecase.GetSettingsUseCase
import com.fairlaunch.domain.usecase.UpdateMapPointUseCase
import com.fairlaunch.domain.usecase.UpdateSettingsUseCase
import com.fairlaunch.worker.LocationWorkScheduler
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
}
