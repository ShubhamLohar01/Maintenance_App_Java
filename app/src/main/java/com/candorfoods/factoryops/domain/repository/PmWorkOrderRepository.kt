package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface PmWorkOrderRepository {

    fun observeAll(): Flow<List<PmWorkOrder>>
    fun observeForTechnician(techId: String, statuses: List<PmWoStatus>): Flow<List<PmWorkOrder>>
    fun observeByStatus(status: PmWoStatus): Flow<List<PmWorkOrder>>
    fun observeById(id: String): Flow<PmWorkOrder?>
    suspend fun getById(id: String): PmWorkOrder?
    suspend fun findOpenForPlan(planId: String): List<PmWorkOrder>
    suspend fun getAllNonClosed(): List<PmWorkOrder>

    /**
     * Inserts a freshly generated WO with snapshotted task logs.
     * Used by the scheduler when a plan trips its trigger.
     */
    suspend fun insertGeneratedWo(wo: PmWorkOrder): Result<PmWorkOrder>

    suspend fun updateStatusAndTier(id: String, status: PmWoStatus, tier: Int)
    suspend fun updateEscalationTier(id: String, tier: Int)

    // ─── Phase 2C-2 — execution + supervisor approval actions ─────────────────

    suspend fun acknowledge(woId: String): Result<Unit>
    suspend fun start(woId: String): Result<Unit>
    suspend fun logTaskResult(
        logId: String,
        woId: String,
        status: com.candorfoods.factoryops.domain.model.PmTaskLogStatus,
        measurementValue: Double?,
        photoLocalPath: String?,
        notes: String?,
        userId: String
    ): Result<Unit>
    suspend fun logSpare(
        woId: String,
        spareName: String,
        quantityUsed: Double,
        unit: String,
        notes: String?,
        userId: String
    ): Result<Unit>
    suspend fun deleteSpare(spareId: String, woId: String): Result<Unit>
    suspend fun submit(woId: String, finalNotes: String?): Result<Unit>
    suspend fun supervisorApprove(woId: String, supervisorId: String, supervisorName: String): Result<Unit>
    suspend fun supervisorReject(woId: String, supervisorId: String, supervisorName: String, notes: String): Result<Unit>

    /** Q3 — autocomplete suggestions ordered by historical usage frequency. */
    suspend fun suggestSpareNames(query: String, limit: Int = 10): List<String>

    // ─── Phase 2C-3 — QC inbox + QC actions ───────────────────────────────────

    /** PMs at PENDING_QC with no QC user assigned. Plant scoping is a VM concern. */
    fun observePendingQc(): Flow<List<PmWorkOrder>>

    /** PMs picked up by [userId] and not yet decided. */
    fun observeQcAcknowledgedByUser(userId: String): Flow<List<PmWorkOrder>>

    /** PMs decided by [userId] since [sinceMillis] (approved or disapproved). */
    fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<PmWorkOrder>>

    /** QC member acknowledges (picks up) the PM. */
    suspend fun qcAcknowledge(woId: String, userId: String, userName: String): Result<Unit>

    /** QC head overrides another QC member's pickup and takes the PM. */
    suspend fun qcOverrideAcknowledge(woId: String, userId: String, userName: String): Result<Unit>

    /**
     * QC approve — terminal. Flips PM to QC_APPROVED, machine to IDLE.
     */
    suspend fun qcApprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        notes: String?
    ): Result<Unit>

    /**
     * QC disapprove — bounces the PM back to the supervisor's Approvals tab.
     * Status returns to SUBMITTED; the supervisor's review screen will show
     * a "QC sent this back: <reason>" banner because qcDecidedAt is set.
     * Machine remains PENDING_QC (still being reviewed by supervisor).
     */
    suspend fun qcDisapprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        reason: String
    ): Result<Unit>

    /** Phase 2C-4 — bump escalation tier (called by scheduler when tier changes). */
    suspend fun bumpTier(woId: String, newTier: Int): Result<Unit>
}
