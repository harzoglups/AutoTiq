package com.cussou.autotiq.domain.model

enum class EventType {
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
    ALERT_SENT,
    ALERT_SKIPPED,
    WORKER_RUN,
    TRACKING_CHANGED,
    GEOFENCES_REGISTERED,
    BOOT,
    ERROR,
}

data class EventLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val type: EventType,
    val message: String,
)
