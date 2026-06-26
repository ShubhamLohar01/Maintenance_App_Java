package com.candorfoods.factoryops.domain.usecase.breakdown

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import javax.inject.Inject

class RaiseBreakdownFlagUseCase @Inject constructor(
    private val breakdownRepository: BreakdownRepository
) {
    suspend operator fun invoke(
        machineId: String,
        severity: Severity,
        description: String,
        beforePhotoLocalPath: String?
    ): Result<BreakdownFlag> {
        if (description.isBlank()) return Result.Error("Description cannot be empty")
        return breakdownRepository.raiseBreakdownFlag(machineId, severity, description, beforePhotoLocalPath)
    }
}
