package com.cussou.autotiq.data.repository

import android.content.Context
import android.util.Log
import com.cussou.autotiq.data.local.AutoTiqDatabase
import com.cussou.autotiq.data.local.dao.EventLogDao
import com.cussou.autotiq.data.local.entity.EventLogEntity
import com.cussou.autotiq.domain.model.EventLogEntry
import com.cussou.autotiq.domain.model.EventType
import com.cussou.autotiq.domain.repository.EventLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EventLogRepositoryImpl @Inject constructor(
    private val dao: EventLogDao
) : EventLogRepository {

    companion object {
        private const val TAG = "EventLogRepository"
        private const val MAX_ENTRIES = 200
    }

    override fun getRecentEvents(): Flow<List<EventLogEntry>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun log(type: EventType, message: String, replaceSameType: Boolean) {
        try {
            if (replaceSameType) {
                dao.deleteByType(type.name)
            }
            dao.insert(
                EventLogEntity(
                    timestamp = System.currentTimeMillis(),
                    type = type.name,
                    message = message
                )
            )
            dao.trimTo(MAX_ENTRIES)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write event log entry", e)
        }
    }

    override suspend fun clear() {
        try {
            dao.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear event log", e)
        }
    }

    private fun EventLogEntity.toDomain(): EventLogEntry {
        val eventType = try {
            EventType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            EventType.ERROR
        }
        return EventLogEntry(id = id, timestamp = timestamp, type = eventType, message = message)
    }
}

/**
 * Event log for components that cannot use Hilt injection (BroadcastReceivers, GeofenceManager).
 * Shares the database singleton with the Hilt-provided instance.
 */
fun Context.eventLog(): EventLogRepository {
    return EventLogRepositoryImpl(AutoTiqDatabase.getInstance(this).eventLogDao())
}
