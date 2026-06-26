package com.candorfoods.factoryops.domain.usecase.breakdown

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import javax.inject.Inject

class AcknowledgeBreakdownUseCase @Inject constructor(
    private val breakdownRepository: BreakdownRepository
) {
    suspend operator fun invoke(flagId: String, userId: String, userName: String): Result<Unit> =
        breakdownRepository.acknowledgeFlag(flagId, userId, userName)
}
