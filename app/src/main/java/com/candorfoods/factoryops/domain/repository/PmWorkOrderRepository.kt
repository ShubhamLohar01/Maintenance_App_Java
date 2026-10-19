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
     * Pull this technician's PM work orders from the backend (mt_pm_work_order via
     * GET /pm/work-orders?technician_id=) into Room, so a plan a supervisor assigned on
     * another device actually surfaces here. Best-effort + offline-safe: the observe*
     * flows keep serving the local cache regardless.
     */
    suspend fun refreshForTechnician(techId: String): Result<Unit>

    /**
     * Pull work orders in the given statuses from the backend into Room — the
     * supervisor's Approvals tab (SUBMITTED) and the QC inbox (PENDING_QC) use this so
     * cross-device review works. Best-effort + offline-safe.
     */
    suspend fun refreshByStatuses(statuses: List<PmWoStatus>): Result<Unit>

    /**
     * All QC-approved (CLOSED) work orders, org-wide — a one-shot NETWORK fetch (not Room)
     * for the QC "History" archive. A CLOSED WO carries the QC approver
     * (qcAcknowledgedByName) + closedAt, so it renders as a decided record.
     */
    suspend fun qcClosedHistory(): Result<List<PmWorkOrder>>

    /**
     * Re-fetch ONE work order (GET /pm/work-orders/{id}) into Room — called when a
     * detail screen opens so a state change made on another device (supervisor
     * rejection, QC bounce) is visible before acting. Best-effort + offline-safe.
     */
    suspend fun refreshWorkOrder(id: String): Result<Unit>

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
    /**
     * Persist one item's "Is good?" verdict + remark. `status` = PASS (good) or FAIL
     * (not good); `notes` is the technician's remark, required by the caller when FAIL.
     */
    suspend fun logTaskResult(
        logId: String,
        woId: String,
        status: com.candorfoods.factoryops.domain.model.PmTaskLogStatus,
        notes: String?,
        userId: String
    ): Result<Unit>
    /**
     * Log a spare consumed by this PM. [pick] comes from the warehouse-scoped
     * picker (there is no free-text path any more), so the line carries the stock
     * row it will decrement when the PM is submitted.
     *
     * Logging the same part twice sums into the existing line rather than adding a
     * duplicate. Fails when the WO already holds [MAX_WO_SPARES] lines.
     */
    suspend fun logSpare(
        woId: String,
        pick: com.candorfoods.factoryops.domain.model.spareparts.SparePartPick,
        quantityUsed: Double,
        notes: String?,
        userId: String
    ): Result<Unit>
    suspend fun deleteSpare(spareId: String, woId: String): Result<Unit>

    /**
     * Stock this technician may consume, for [assetId]'s work order. Served from
     * the backend and cached, so the picker still opens offline with last-synced
     * quantities — advisory only, since the server re-checks at submit.
     */
    suspend fun sparePicker(assetId: String?): com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog

    /** Submit the completed checklist. `overallResult` = machine-level "PASS"/"FAIL". */
    suspend fun submit(woId: String, finalNotes: String?, overallResult: String?): Result<Unit>
    suspend fun supervisorApprove(woId: String, supervisorId: String, supervisorName: String): Result<Unit>
    suspend fun supervisorReject(woId: String, supervisorId: String, supervisorName: String, notes: String): Result<Unit>

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

    /** GET /pm/work-orders?status= (no technician filter) — every work order regardless
     *  of assignee, for Head Reports PM History. Online-only, no Room. */
    suspend fun history(status: String? = null): Result<List<PmWorkOrder>>
}
