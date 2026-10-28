package com.cussou.autotiq.domain.repository

import com.cussou.autotiq.domain.model.EventLogEntry
import com.cussou.autotiq.domain.model.EventType
import kotlinx.coroutines.flow.Flow

/**
 * Diagnostic journal of what the background detection did (geofence events, alerts, worker runs).
 * Used to find out why an alert was missed without needing adb.
 */
interface EventLogRepository {
    fun getRecentEvents(): Flow<List<EventLogEntry>>

    /**
     * Records an event. Never throws: logging must not break the detection itself.
     *
     * @param replaceSameType when true, previous entries of the same type are removed first, so a
     * frequent "heartbeat" event keeps a single row instead of flooding the journal.
     */
    suspend fun log(type: EventType, message: String, replaceSameType: Boolean = false)

    suspend fun clear()
}
