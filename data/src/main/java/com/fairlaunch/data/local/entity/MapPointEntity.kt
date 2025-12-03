package com.fairlaunch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "map_points")
data class MapPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val startHour: Int,
    val endHour: Int,
    val createdAt: Long
)
