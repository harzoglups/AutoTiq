package com.cussou.autotiq.di

import android.content.Context
import com.cussou.autotiq.data.local.AutoTiqDatabase
import com.cussou.autotiq.data.local.dao.MapPointDao
import com.cussou.autotiq.data.local.dao.ProximityStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoTiqDatabase {
        // Use the singleton instance so BroadcastReceivers share the same database
        return AutoTiqDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMapPointDao(database: AutoTiqDatabase): MapPointDao {
        return database.mapPointDao()
    }

    @Provides
    @Singleton
    fun provideProximityStateDao(database: AutoTiqDatabase): ProximityStateDao {
        return database.proximityStateDao()
    }
}
