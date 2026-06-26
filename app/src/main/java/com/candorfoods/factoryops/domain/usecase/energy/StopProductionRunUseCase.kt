package com.candorfoods.factoryops.domain.usecase.energy

import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import javax.inject.Inject

class StopProductionRunUseCase @Inject constructor(
    private val energyRepository: EnergyRepository
) {
    suspend operator fun invoke(runId: String): Result<ProductionRun> =
        energyRepository.stopProductionRun(runId, System.currentTimeMillis())
}
