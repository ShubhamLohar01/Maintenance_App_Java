package com.candorfoods.factoryops.domain.usecase.breakdown

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import javax.inject.Inject

class ResolveBreakdownUseCase @Inject constructor(
    private val breakdownRepository: BreakdownRepository
) {
    suspend operator fun invoke(
        flagId: String,
        userId: String,
        userName: String,
        partsUsed: String,
        afterPhotoLocalPath: String?
    ): Result<Unit> {
        val trimmed = partsUsed.trim()
        if (trimmed.length < 4) return Result.Error("Describe parts/work in at least 4 characters.")
        if (afterPhotoLocalPath.isNullOrBlank()) return Result.Error("After-repair photo is required.")
        return breakdownRepository.resolveFlag(flagId, userId, userName, trimmed, afterPhotoLocalPath)
    }
}
