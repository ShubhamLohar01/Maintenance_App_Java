package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface MachineRepository {
    fun getAssignedMachines(): Flow<Result<List<Machine>>>
    fun getMachineById(machineId: String): Flow<Machine?>
    fun observeAllMachines(): Flow<List<Machine>>
    suspend fun syncMachines(): Result<Unit>
    suspend fun updateMachineStatus(machineId: String, status: String)
}
