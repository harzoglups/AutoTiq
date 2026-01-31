package com.cussou.autotiq.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cussou.autotiq.data.local.entity.MapPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapPointDao {
    @Query("SELECT * FROM map_points ORDER BY createdAt DESC")
    fun getAllPoints(): Flow<List<MapPointEntity>>
    
    @Query("SELECT * FROM map_points ORDER BY createdAt DESC")
    suspend fun getAllPointsOnce(): List<MapPointEntity>
    
    @Query("SELECT * FROM map_points WHERE id = :id")
    suspend fun getPointById(id: Long): MapPointEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: MapPointEntity): Long
    
    @Query("DELETE FROM map_points WHERE id = :id")
    suspend fun deletePoint(id: Long)
    
    @Update
    suspend fun updatePoint(point: MapPointEntity)
}
