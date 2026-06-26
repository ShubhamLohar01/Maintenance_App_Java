package com.candorfoods.factoryops.domain.usecase.energy

import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTodaysRunsUseCase @Inject constructor(
    private val energyRepository: EnergyRepository
) {
    operator fun invoke(machineId: String): Flow<List<ProductionRun>> =
        energyRepository.getTodaysRuns(machineId)
}
