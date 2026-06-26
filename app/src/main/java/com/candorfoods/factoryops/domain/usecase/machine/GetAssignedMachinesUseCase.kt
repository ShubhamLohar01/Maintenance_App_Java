package com.candorfoods.factoryops.domain.usecase.machine

import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAssignedMachinesUseCase @Inject constructor(
    private val machineRepository: MachineRepository
) {
    operator fun invoke(): Flow<Result<List<Machine>>> = machineRepository.getAssignedMachines()
}
