package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.PmWorkOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmWorkOrderDao {

    @Query("SELECT * FROM pm_work_orders ORDER BY scheduledDate ASC")
    fun observeAll(): Flow<List<PmWorkOrderEntity>>

    @Query("SELECT * FROM pm_work_orders WHERE id = :id")
    suspend fun getById(id: String): PmWorkOrderEntity?

    @Query("SELECT * FROM pm_work_orders WHERE id = :id")
    fun observeById(id: String): Flow<PmWorkOrderEntity?>

    @Query("SELECT * FROM pm_work_orders WHERE assignedTechnicianId = :techId AND status IN (:statuses) ORDER BY scheduledDate ASC")
    fun observeForTechnician(techId: String, statuses: List<String>): Flow<List<PmWorkOrderEntity>>

    @Query("SELECT * FROM pm_work_orders WHERE status = :status ORDER BY scheduledDate ASC")
    fun observeByStatus(status: String): Flow<List<PmWorkOrderEntity>>

    @Query("SELECT * FROM pm_work_orders WHERE planId = :planId AND status IN (:statuses)")
    suspend fun findOpenForPlan(planId: String, statuses: List<String>): List<PmWorkOrderEntity>

    @Query("SELECT * FROM pm_work_orders WHERE status NOT IN ('CLOSED','CANCELLED','QC_APPROVED')")
    suspend fun getAllNonClosed(): List<PmWorkOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wo: PmWorkOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workOrders: List<PmWorkOrderEntity>)

    @Query("UPDATE pm_work_orders SET status = :status, escalationTier = :tier WHERE id = :id")
    suspend fun updateStatusAndTier(id: String, status: String, tier: Int)

    @Query("UPDATE pm_work_orders SET escalationTier = :tier WHERE id = :id")
    suspend fun updateEscalationTier(id: String, tier: Int)

    @Query("SELECT COUNT(*) FROM pm_work_orders")
    suspend fun count(): Int

    // ─── Phase 2C-2 — action-driven status transitions ────────────────────────

    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'ACKNOWLEDGED', acknowledgedAt = :ts, syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markAcknowledged(id: String, ts: Long)

    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'IN_PROGRESS', startedAt = COALESCE(startedAt, :ts), syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markStarted(id: String, ts: Long)

    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'SUBMITTED', submittedAt = :ts, finalNotes = :finalNotes, syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markSubmitted(id: String, ts: Long, finalNotes: String?)

    /**
     * Phase 2C-3: supervisor approval IS the QC handoff.
     * Status flips to PENDING_QC (not SUPERVISOR_APPROVED). Any prior QC
     * bounce state is cleared so the WO appears fresh in the QC inbox.
     */
    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'PENDING_QC',
            supervisorApprovedBy = :userId,
            supervisorApprovedByName = :userName,
            supervisorApprovedAt = :ts,
            supervisorRejectedAt = NULL,
            supervisorRejectionNotes = NULL,
            qcDecidedBy = NULL,
            qcDecidedByName = NULL,
            qcDecidedAt = NULL,
            qcDecisionNotes = NULL,
            qcAcknowledgedBy = NULL,
            qcAcknowledgedByName = NULL,
            qcAcknowledgedAt = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markSupervisorApproved(id: String, userId: String, userName: String, ts: Long)

    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'IN_PROGRESS',
            supervisorRejectedAt = :ts,
            supervisorRejectionNotes = :notes,
            submittedAt = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markSupervisorRejected(id: String, ts: Long, notes: String)

    // ─── Phase 2C-3 — QC inbox observers ──────────────────────────────────────

    @Query("""
        SELECT * FROM pm_work_orders
        WHERE status = 'PENDING_QC' AND qcAcknowledgedBy IS NULL
        ORDER BY submittedAt ASC
    """)
    fun observePendingQc(): Flow<List<PmWorkOrderEntity>>

    @Query("""
        SELECT * FROM pm_work_orders
        WHERE status = 'PENDING_QC' AND qcAcknowledgedBy = :userId
        ORDER BY qcAcknowledgedAt DESC
    """)
    fun observeQcAcknowledgedByUser(userId: String): Flow<List<PmWorkOrderEntity>>

    @Query("""
        SELECT * FROM pm_work_orders
        WHERE qcDecidedBy = :userId AND qcDecidedAt >= :sinceMillis
        ORDER BY qcDecidedAt DESC
    """)
    fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<PmWorkOrderEntity>>

    // ─── Phase 2C-3 — QC action transitions ───────────────────────────────────

    @Query("""
        UPDATE pm_work_orders
        SET qcAcknowledgedBy = :userId,
            qcAcknowledgedByName = :userName,
            qcAcknowledgedAt = :ts,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcAcknowledged(id: String, userId: String, userName: String, ts: Long)

    @Query("""
        UPDATE pm_work_orders
        SET qcAcknowledgedBy = :newUserId,
            qcAcknowledgedByName = :newUserName,
            qcAcknowledgedAt = :ts,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcOverrideAcknowledged(id: String, newUserId: String, newUserName: String, ts: Long)

    @Query("""
        UPDATE pm_work_orders
        SET status = 'QC_APPROVED',
            qcDecidedBy = :userId,
            qcDecidedByName = :userName,
            qcDecidedAt = :ts,
            qcDecisionNotes = :notes,
            qcAfterPhotoLocalPath = :photoPath,
            qcChecklistJson = :checklistJson,
            closedAt = :ts,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcApproved(
        id: String,
        userId: String,
        userName: String,
        ts: Long,
        notes: String?,
        photoPath: String?,
        checklistJson: String
    )

    @Query("""
        UPDATE pm_work_orders
        SET status = 'SUBMITTED',
            qcDecidedBy = :userId,
            qcDecidedByName = :userName,
            qcDecidedAt = :ts,
            qcDecisionNotes = :reason,
            qcAfterPhotoLocalPath = :photoPath,
            qcChecklistJson = :checklistJson,
            qcAcknowledgedBy = NULL,
            qcAcknowledgedByName = NULL,
            qcAcknowledgedAt = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcDisapproved(
        id: String,
        userId: String,
        userName: String,
        ts: Long,
        reason: String,
        photoPath: String?,
        checklistJson: String
    )
}

