package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.ActiveRun
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface EnergyRepository {
    /** Live: production runs started and not yet stopped, across all operators. */
    suspend fun getActiveRuns(): Result<List<ActiveRun>>
    suspend fun startProductionRun(
        assetId: String, assetName: String, ratedKw: Double?, scheduledEndAt: Long
    ): Result<ProductionRun>
    suspend fun stopProductionRun(runId: String, endedAt: Long): Result<ProductionRun>
    /** Stop a run that lives only on the backend (no local row) by its server run_id. */
    suspend fun stopActiveRunById(serverRunId: String, endedAt: Long): Result<Unit>
    fun getTodaysRuns(machineId: String): Flow<List<ProductionRun>>
    fun getActiveRunForMachine(machineId: String): Flow<ProductionRun?>
    fun getMachineHistory(machineId: String, fromDate: Long, toDate: Long): Flow<Result<List<ProductionRun>>>
    fun getPendingSyncCount(): Flow<Int>
    suspend fun syncPendingRuns(): Result<Unit>
}
