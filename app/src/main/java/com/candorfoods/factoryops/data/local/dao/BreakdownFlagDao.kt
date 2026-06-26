package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.BreakdownFlagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownFlagDao {

    @Query("SELECT * FROM breakdown_flags WHERE syncStatus != 'SYNCED' ORDER BY raisedAt DESC")
    fun getPendingFlags(): Flow<List<BreakdownFlagEntity>>

    /**
     * Asset_ids that currently have an unresolved breakdown (anything not yet
     * QC-approved). Keyed directly on machineId (= asset_id) with NO join to the
     * legacy `machines` table, so it works for the asset register. Drives the
     * operator Home "Under Breakdown" badge + Start blocking.
     */
    @Query("SELECT DISTINCT machineId FROM breakdown_flags WHERE status != 'CLOSED'")
    fun observeActiveFlagMachineIds(): Flow<List<String>>

    /** Asset_ids whose breakdown is awaiting QC verification (status = PENDING_QC). */
    @Query("SELECT DISTINCT machineId FROM breakdown_flags WHERE status = 'PENDING_QC'")
    fun observePendingQcMachineIds(): Flow<List<String>>

    @Query("SELECT * FROM breakdown_flags WHERE syncStatus != 'SYNCED' ORDER BY raisedAt DESC")
    suspend fun getPendingFlagsOnce(): List<BreakdownFlagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flag: BreakdownFlagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(flags: List<BreakdownFlagEntity>)

    /** Remove leftover mock seed flags (ids like "seed-flag-001") from earlier mock runs. */
    @Query("DELETE FROM breakdown_flags WHERE id LIKE 'seed-%'")
    suspend fun deleteSeedFlags()

    @Query("UPDATE breakdown_flags SET syncStatus = :syncStatus WHERE id = :flagId")
    suspend fun updateSyncStatus(flagId: String, syncStatus: String)

    @Query("""
        SELECT * FROM breakdown_flags
        WHERE status = 'OPEN'
          AND machineId IN (SELECT id FROM machines WHERE plantId = :plantId)
        ORDER BY raisedAt DESC
    """)
    fun observeOpenFlagsByPlant(plantId: String): Flow<List<BreakdownFlagEntity>>

    /**
     * Open work pool, no join to the legacy `machines` table — so asset-register
     * flags (machineId = asset_id like "W202-0123") are included. The technician's
     * incoming-breakdowns list uses this; names resolve from the asset register.
     * REOPENED is included: a QC-rejected ticket returns to the Open pool so any
     * technician can pick it up for rework.
     */
    @Query("SELECT * FROM breakdown_flags WHERE status IN ('OPEN', 'REOPENED') ORDER BY raisedAt DESC")
    fun observeAllOpenFlags(): Flow<List<BreakdownFlagEntity>>

    /** Every breakdown in Room (all statuses), newest first — the Breakdown records page. */
    @Query("SELECT * FROM breakdown_flags ORDER BY raisedAt DESC")
    fun observeAll(): Flow<List<BreakdownFlagEntity>>

    /**
     * Technician's "My Active" tab — tickets this technician has acknowledged and is
     * actively working. QC-rejected tickets (REOPENED) are NOT here: they return to
     * the Open pool (see observeAllOpenFlags) so any technician can pick them up.
     */
    @Query("""
        SELECT * FROM breakdown_flags
        WHERE status = 'ACKNOWLEDGED'
          AND acknowledgedBy = :userId
        ORDER BY acknowledgedAt DESC
    """)
    fun observeAcknowledgedFlagsByUser(userId: String): Flow<List<BreakdownFlagEntity>>

    /**
     * Technician's "Resolved" tab — successfully closed (QC-approved) tickets only.
     * REOPENED tickets are mid-flight and live in the Open tab. No join to the legacy
     * `machines` table, so asset-register flags are included; `plantId` is accepted
     * for API compatibility but scoping is done in the ViewModel.
     */
    @Query("""
        SELECT * FROM breakdown_flags
        WHERE status = 'CLOSED'
          AND qcDecidedAt IS NOT NULL
          AND qcDecidedAt >= :sinceMillis
        ORDER BY qcDecidedAt DESC
    """)
    fun observeResolvedFlags(sinceMillis: Long): Flow<List<BreakdownFlagEntity>>

    @Query("SELECT * FROM breakdown_flags WHERE id = :flagId")
    fun observeFlagById(flagId: String): Flow<BreakdownFlagEntity?>

    @Query("SELECT * FROM breakdown_flags WHERE id = :flagId")
    suspend fun getFlagByIdOnce(flagId: String): BreakdownFlagEntity?

    @Query("""
        SELECT * FROM breakdown_flags
        WHERE machineId = :machineId
          AND status != 'CLOSED'
        ORDER BY raisedAt DESC
        LIMIT 1
    """)
    fun observeActiveFlagForMachine(machineId: String): Flow<BreakdownFlagEntity?>

    @Query("""
        SELECT * FROM breakdown_flags
        WHERE machineId = :machineId
        ORDER BY raisedAt DESC
        LIMIT 1
    """)
    fun observeLatestFlagForMachine(machineId: String): Flow<BreakdownFlagEntity?>

    @Query("""
        SELECT * FROM breakdown_flags
        WHERE status = 'OPEN'
          AND raisedAt > :sinceMillis
          AND machineId IN (SELECT id FROM machines WHERE plantId = :plantId)
        ORDER BY raisedAt ASC
    """)
    suspend fun getNewOpenFlagsSince(plantId: String, sinceMillis: Long): List<BreakdownFlagEntity>

    @Query("""
        UPDATE breakdown_flags
        SET status = 'ACKNOWLEDGED',
            acknowledgedBy = :userId,
            acknowledgedByName = :userName,
            acknowledgedAt = :acknowledgedAt,
            syncStatus = :syncStatus
        WHERE id = :flagId
    """)
    suspend fun acknowledgeFlag(
        flagId: String,
        userId: String,
        userName: String,
        acknowledgedAt: Long,
        syncStatus: String
    )

    /**
     * Technician resolves a ticket. Status moves to PENDING_QC and machine to PENDING_QC.
     * If the ticket was REOPENED (i.e., a re-resolve after QC kicked back), all qc_* fields
     * are cleared so a fresh QC cycle starts. Prior cycle's QC audit is overwritten — Phase 3
     * backend will preserve the full cycle history.
     */
    @Query("""
        UPDATE breakdown_flags
        SET status = 'PENDING_QC',
            resolvedBy = :userId,
            resolvedByName = :userName,
            resolvedAt = :resolvedAt,
            partsUsed = :partsUsed,
            afterPhotoLocalPath = :afterPhotoPath,
            qcStatus = NULL,
            qcAcknowledgedBy = NULL,
            qcAcknowledgedByName = NULL,
            qcAcknowledgedAt = NULL,
            qcDecidedBy = NULL,
            qcDecidedByName = NULL,
            qcDecidedAt = NULL,
            qcDecisionNotes = NULL,
            qcAfterPhotoLocalPath = NULL,
            qcChecklistJson = NULL,
            qcOverrideFromUserId = NULL,
            qcOverrideFromName = NULL,
            qcOverrideAt = NULL,
            syncStatus = :syncStatus
        WHERE id = :flagId
    """)
    suspend fun resolveFlag(
        flagId: String,
        userId: String,
        userName: String,
        resolvedAt: Long,
        partsUsed: String,
        afterPhotoPath: String?,
        syncStatus: String
    )

    // -------- QC verification ---------------------------------------------------

    /** "Awaiting QC" tab — PENDING_QC tickets that no QC user has picked up yet, FIFO.
     * No join to the legacy `machines` table, so asset-register flags (machineId =
     * asset_id like "W202-0123") are included; `plantId` is accepted for API
     * compatibility but scoping is done in the ViewModel via the asset register. */
    @Query("""
        SELECT * FROM breakdown_flags
        WHERE status = 'PENDING_QC'
          AND qcAcknowledgedBy IS NULL
        ORDER BY resolvedAt ASC
    """)
    fun observePendingQc(): Flow<List<BreakdownFlagEntity>>

    /** QC user's "My Active" tab — picked up by this QC user, not yet decided. */
    @Query("""
        SELECT * FROM breakdown_flags
        WHERE status = 'PENDING_QC'
          AND qcAcknowledgedBy = :userId
        ORDER BY qcAcknowledgedAt ASC
    """)
    fun observeQcAcknowledgedByUser(userId: String): Flow<List<BreakdownFlagEntity>>

    /** QC user's "Done today" tab — decided (approved or disapproved) by this user since `sinceMillis`. */
    @Query("""
        SELECT * FROM breakdown_flags
        WHERE qcDecidedBy = :userId
          AND qcDecidedAt IS NOT NULL
          AND qcDecidedAt >= :sinceMillis
        ORDER BY qcDecidedAt DESC
    """)
    fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<BreakdownFlagEntity>>

    @Query("""
        UPDATE breakdown_flags
        SET qcStatus = 'PENDING',
            qcAcknowledgedBy = :userId,
            qcAcknowledgedByName = :userName,
            qcAcknowledgedAt = :ackedAt,
            syncStatus = :syncStatus
        WHERE id = :flagId
    """)
    suspend fun qcAcknowledge(
        flagId: String,
        userId: String,
        userName: String,
        ackedAt: Long,
        syncStatus: String
    )

    /**
     * QC_HEAD override: replace the current QC acknowledger with the head, and
     * record the prior holder for audit (qcOverrideFrom*).
     */
    @Query("""
        UPDATE breakdown_flags
        SET qcAcknowledgedBy = :newUserId,
            qcAcknowledgedByName = :newUserName,
            qcAcknowledgedAt = :ackedAt,
            qcOverrideFromUserId = qcAcknowledgedBy,
            qcOverrideFromName = qcAcknowledgedByName,
            qcOverrideAt = :ackedAt,
            syncStatus = :syncStatus
        WHERE id = :flagId
    """)
    suspend fun qcOverrideAcknowledge(
        flagId: String,
        newUserId: String,
        newUserName: String,
        ackedAt: Long,
        syncStatus: String
    )

    @Query("""
        UPDATE breakdown_flags
        SET status = 'CLOSED',
            qcStatus = 'APPROVED',
            qcDecidedBy = :userId,
            qcDecidedByName = :userName,
            qcDecidedAt = :decidedAt,
            qcDecisionNotes = :notes,
            qcAfterPhotoLocalPath = :photoPath,
            qcChecklistJson = :checklistJson,
            syncStatus = :syncStatus
        WHERE id = :flagId
    """)
    suspend fun qcApprove(
        flagId: String,
        userId: String,
        userName: String,
        decidedAt: Long,
        notes: String?,
        photoPath: String?,
        checklistJson: String,
        syncStatus: String
    )

    @Query("""
        UPDATE breakdown_flags
        SET status = 'REOPENED',
            qcStatus = 'REJECTED',
            qcDecidedBy = :userId,
            qcDecidedByName = :userName,
            qcDecidedAt = :decidedAt,
            qcDecisionNotes = :reason,
            qcAfterPhotoLocalPath = :photoPath,
            qcChecklistJson = :checklistJson,
            syncStatus = :syncStatus
        WHERE id = :flagId
    """)
    suspend fun qcDisapprove(
        flagId: String,
        userId: String,
        userName: String,
        decidedAt: Long,
        reason: String,
        photoPath: String?,
        checklistJson: String,
        syncStatus: String
    )
}
