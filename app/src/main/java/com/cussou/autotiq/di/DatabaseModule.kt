package com.cussou.autotiq.di

import android.content.Context
import androidx.room.Room
import com.cussou.autotiq.data.local.AutoTiqDatabase
import com.cussou.autotiq.data.local.MIGRATION_1_2
import com.cussou.autotiq.data.local.MIGRATION_2_3
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
        return Room.databaseBuilder(
            context,
            AutoTiqDatabase::class.java,
            "autotiq_database"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
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
