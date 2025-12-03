package com.fairlaunch.data.mapper

import com.fairlaunch.data.local.entity.MapPointEntity
import com.fairlaunch.domain.model.MapPoint

fun MapPointEntity.toDomain(): MapPoint {
    return MapPoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        name = name,
        startHour = startHour,
        endHour = endHour,
        createdAt = createdAt
    )
}

fun MapPoint.toEntity(): MapPointEntity {
    return MapPointEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        name = name,
        startHour = startHour,
        endHour = endHour,
        createdAt = createdAt
    )
}
