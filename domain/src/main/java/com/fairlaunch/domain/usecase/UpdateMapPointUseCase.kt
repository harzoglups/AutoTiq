package com.fairlaunch.domain.usecase

import com.fairlaunch.domain.model.MapPoint
import com.fairlaunch.domain.repository.MapPointRepository
import com.fairlaunch.domain.util.Result

class UpdateMapPointUseCase(
    private val repository: MapPointRepository
) {
    suspend operator fun invoke(point: MapPoint): Result<Unit> {
        return repository.updatePoint(point)
    }
}
