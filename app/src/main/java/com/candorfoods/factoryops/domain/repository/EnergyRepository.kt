package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.ActiveRun
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.RecessState
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

    // ─── Recess (pause/resume), operator-side ────────────────────────────────────
    /** Operator-side pause/resume session state for a machine (null = no session). */
    fun observeRecessState(machineId: String): Flow<RecessState?>
    /** Pause: stop the current run segment and bank its active time on the session. */
    suspend fun recessProduction(machineId: String): Result<Unit>
    /** Resume: open a new run segment, keeping the banked active time. */
    suspend fun resumeProduction(
        assetId: String, assetName: String, ratedKw: Double?, scheduledEndAt: Long
    ): Result<Unit>
    /** End the shift: stop the active segment (if any) and clear the session. */
    suspend fun endProductionSession(machineId: String): Result<Unit>
    /** Drop any leftover session row (defensive cleanup when starting fresh). */
    suspend fun clearRecessSession(machineId: String)
}
