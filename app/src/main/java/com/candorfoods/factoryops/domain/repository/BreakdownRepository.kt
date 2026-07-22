package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncQueueItem
import kotlinx.coroutines.flow.Flow

interface BreakdownRepository {

    suspend fun raiseBreakdownFlag(
        machineId: String,
        severity: Severity,
        description: String,
        beforePhotoLocalPath: String?
    ): Result<BreakdownFlag>

    fun observeOpenFlagsByPlant(plantId: String): Flow<List<BreakdownFlag>>
    /** All OPEN flags incl. asset-register ones (no legacy machines-table join). */
    fun observeAllOpenFlags(): Flow<List<BreakdownFlag>>
    /** Every breakdown in Room (all statuses) for the Breakdown records page. */
    fun observeAllFlags(): Flow<List<BreakdownFlag>>
    fun observeAcknowledgedFlagsByUser(userId: String): Flow<List<BreakdownFlag>>
    fun observeResolvedFlagsByPlant(plantId: String, sinceMillis: Long): Flow<List<BreakdownFlag>>
    fun observeFlagById(flagId: String): Flow<BreakdownFlag?>
    fun observeActiveFlagForMachine(machineId: String): Flow<BreakdownFlag?>
    fun observeLatestFlagForMachine(machineId: String): Flow<BreakdownFlag?>

    /** asset_ids with an unresolved breakdown (not yet QC-approved; includes PENDING_QC). */
    fun observeMachinesUnderBreakdown(): Flow<Set<String>>

    /** asset_ids whose breakdown is awaiting QC verification (status = PENDING_QC). */
    fun observeMachinesUnderQc(): Flow<Set<String>>

    // QC inbox observers
    fun observePendingQcByPlant(plantId: String): Flow<List<BreakdownFlag>>
    fun observeQcAcknowledgedByUser(userId: String): Flow<List<BreakdownFlag>>
    fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<BreakdownFlag>>

    suspend fun acknowledgeFlag(flagId: String, userId: String, userName: String): Result<Unit>
    suspend fun resolveFlag(
        flagId: String,
        userId: String,
        userName: String,
        partsUsed: String,
        afterPhotoLocalPath: String?
    ): Result<Unit>

    // QC actions
    suspend fun qcAcknowledge(
        flagId: String,
        userId: String,
        userName: String,
        username: String
    ): Result<Unit>
    suspend fun qcOverrideAcknowledge(flagId: String, newUserId: String, newUserName: String): Result<Unit>
    suspend fun qcApprove(
        flagId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        notes: String?
    ): Result<Unit>
    suspend fun qcDisapprove(
        flagId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        reason: String
    ): Result<Unit>

    suspend fun getNewOpenFlagsSince(plantId: String, sinceMillis: Long): List<BreakdownFlag>

    /** Pull open breakdowns from the backend (GET /breakdowns/open) and upsert them
     * into Room so every device/role sees the same tickets and tap-to-detail resolves.
     * plant_id: "W202" / "A185" / "both". Local unsynced edits are preserved. */
    suspend fun refreshOpenFlags(plantId: String = "both"): Result<Unit>

    fun getPendingBreakdowns(): Flow<List<BreakdownFlag>>
    fun getSyncQueue(): Flow<List<SyncQueueItem>>

    suspend fun seedMockOpenFlagsIfEmpty()

    /** Remove leftover mock seed breakdown flags from earlier USE_MOCK_API runs (no-op in mock mode). */
    suspend fun purgeMockSeedFlags()

    /** GET /breakdowns/history — every breakdown in range regardless of status (Head Reports). */
    suspend fun breakdownHistory(
        from: String,
        to: String,
        plantId: String = "both",
        status: String? = null,
    ): Result<List<BreakdownFlag>>

    /** GET /qc/history — every breakdown with a QC decision in range (Head Reports). */
    suspend fun qcActivityHistory(
        from: String,
        to: String,
        plantId: String = "both",
    ): Result<List<BreakdownFlag>>
}
