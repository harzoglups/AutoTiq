package com.fairlaunch.di

import android.content.Context
import androidx.room.Room
import com.fairlaunch.data.local.FairLaunchDatabase
import com.fairlaunch.data.local.MIGRATION_1_2
import com.fairlaunch.data.local.dao.MapPointDao
import com.fairlaunch.data.local.dao.ProximityStateDao
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
    fun provideDatabase(@ApplicationContext context: Context): FairLaunchDatabase {
        return Room.databaseBuilder(
            context,
            FairLaunchDatabase::class.java,
            "fairlaunch_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideMapPointDao(database: FairLaunchDatabase): MapPointDao {
        return database.mapPointDao()
    }

    @Provides
    @Singleton
    fun provideProximityStateDao(database: FairLaunchDatabase): ProximityStateDao {
        return database.proximityStateDao()
    }
}
