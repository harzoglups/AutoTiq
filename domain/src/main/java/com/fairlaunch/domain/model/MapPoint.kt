package com.fairlaunch.domain.model

data class MapPoint(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val startHour: Int = 0, // 0-23, start hour for active window
    val endHour: Int = 23, // 0-23, end hour for active window
    val createdAt: Long = System.currentTimeMillis()
)
