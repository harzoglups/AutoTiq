package com.fairlaunch.domain.usecase

import com.fairlaunch.domain.model.MapPoint
import com.fairlaunch.domain.repository.MapPointRepository
import com.fairlaunch.domain.util.Result

class AddMapPointUseCase(
    private val repository: MapPointRepository
) {
    suspend operator fun invoke(latitude: Double, longitude: Double, name: String? = null): Result<Long> {
        val point = MapPoint(
            latitude = latitude,
            longitude = longitude,
            name = name ?: ""
        )
        return repository.insertPoint(point)
    }
}
