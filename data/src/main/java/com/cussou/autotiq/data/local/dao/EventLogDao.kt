package com.cussou.autotiq.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cussou.autotiq.data.local.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY id DESC")
    fun getAll(): Flow<List<EventLogEntity>>

    @Insert
    suspend fun insert(entity: EventLogEntity)

    @Query("DELETE FROM event_log WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM event_log WHERE id NOT IN (SELECT id FROM event_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM event_log")
    suspend fun clear()
}
