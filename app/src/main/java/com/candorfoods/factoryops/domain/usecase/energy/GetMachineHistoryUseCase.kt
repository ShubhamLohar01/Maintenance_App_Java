package com.candorfoods.factoryops.domain.usecase.energy

import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMachineHistoryUseCase @Inject constructor(
    private val energyRepository: EnergyRepository
) {
    operator fun invoke(
        machineId: String,
        fromDate: Long,
        toDate: Long
    ): Flow<Result<List<ProductionRun>>> =
        energyRepository.getMachineHistory(machineId, fromDate, toDate)
}
