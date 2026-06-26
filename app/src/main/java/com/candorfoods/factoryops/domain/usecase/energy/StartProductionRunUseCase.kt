package com.candorfoods.factoryops.domain.usecase.energy

import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import javax.inject.Inject

class StartProductionRunUseCase @Inject constructor(
    private val energyRepository: EnergyRepository
) {
    suspend operator fun invoke(
        assetId: String,
        assetName: String,
        ratedKw: Double?,
        expectedRunHours: Double,
    ): Result<ProductionRun> {
        val scheduledEndAt = System.currentTimeMillis() + (expectedRunHours * 3_600_000).toLong()
        return energyRepository.startProductionRun(assetId, assetName, ratedKw, scheduledEndAt)
    }
}
