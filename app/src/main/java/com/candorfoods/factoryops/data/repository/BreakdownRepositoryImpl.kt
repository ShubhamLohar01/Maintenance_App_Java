package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.BreakdownFlagEntity
import com.candorfoods.factoryops.data.local.entity.SyncQueueEntity
import com.candorfoods.factoryops.data.remote.api.BreakdownApi
import com.candorfoods.factoryops.data.remote.api.ChecklistApi
import com.candorfoods.factoryops.data.remote.dto.BreakdownEntryDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownSheetDto
import com.candorfoods.factoryops.data.remote.dto.OpenBreakdownDto
import com.candorfoods.factoryops.data.remote.dto.QcAcknowledgeRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcDecisionRequestDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.*
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakdownRepositoryImpl @Inject constructor(
    private val breakdownApi: BreakdownApi,
    private val checklistApi: ChecklistApi,
    private val mtMachineRepository: MtMachineRepository,
    private val db: AppDatabase,
    private val authPreferences: AuthPreferences
) : BreakdownRepository {

    override suspend fun raiseBreakdownFlag(
        machineId: String, severity: Severity,
        description: String, beforePhotoLocalPath: String?
    ): Result<BreakdownFlag> {
        return try {
            val now = System.currentTimeMillis()
            val operatorId = authPreferences.getUserId().orEmpty()

            // Push to the live breakdown register first (POST /breakdowns/flag ->
            // mt_breakdown_records, the live breakdown table). The server id becomes the
            // row's canonical id so QC / detail / cross-device all resolve it. Offline
            // -> fall back to a local UUID and a PENDING sync-queue entry.
            val serverId = pushFlagToBackend(machineId, operatorId, severity, description, now, beforePhotoLocalPath)
            val flagId = serverId ?: UUID.randomUUID().toString()

            val entity = BreakdownFlagEntity(
                id = flagId,
                machineId = machineId,
                operatorId = operatorId,
                severity = severity.name,
                description = description,
                beforePhotoLocalPath = beforePhotoLocalPath,
                raisedAt = now,
                status = BreakdownStatus.OPEN.name,
                acknowledgedBy = null,
                acknowledgedByName = null,
                acknowledgedAt = null,
                resolvedBy = null,
                resolvedByName = null,
                resolvedAt = null,
                partsUsed = null,
                afterPhotoLocalPath = null,
                syncStatus = if (serverId != null) SyncStatus.SYNCED.name else SyncStatus.PENDING.name
            )
            db.breakdownFlagDao().insert(entity)
            db.machineDao().updateStatus(machineId, MachineStatus.PENDING_QC.name, now)
            if (serverId == null) {
                // Backend unreachable — queue it so it can be re-pushed later.
                db.syncQueueDao().insert(
                    SyncQueueEntity(
                        actionType = ActionType.FLAG_RAISE.name,
                        payloadJson = """{"flag_id":"$flagId","machine_id":"$machineId"}""",
                        lastErrorMessage = null,
                        createdAt = now
                    )
                )
            }
            Result.Success(toDomain(entity))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to raise breakdown flag", e)
        }
    }

    /**
     * Push an operator breakdown to POST /breakdowns/flag (mt_breakdown_records,
     * the live breakdown table) — the live ticket pipeline that the Head/QC views and
     * the /breakdowns/open read all use. Sent as multipart/form-data so the operator's
     * before-photo is uploaded to S3 (stored in before_photo_url), not just kept on the
     * device. Returns the server flag id on success, or null on any failure (offline-safe:
     * the caller keeps a local row + PENDING sync).
     */
    private suspend fun pushFlagToBackend(
        machineId: String, operatorId: String, severity: Severity,
        description: String, raisedAt: Long, beforePhotoLocalPath: String?
    ): String? = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val resp = breakdownApi.raiseFlag(
            token,
            machineId = machineId.textPart(),
            operatorId = operatorId.textPart(),
            severity = severity.name.textPart(),
            description = description.textPart(),
            raisedAt = raisedAt.toString().textPart(),
            beforePhoto = photoPart("before_photo", beforePhotoLocalPath),
        )
        resp.id.ifBlank { null }
    } catch (_: Exception) {
        null
    }

    override fun observeOpenFlagsByPlant(plantId: String): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeOpenFlagsByPlant(plantId).map { it.map(::toDomain) }

    override fun observeAllOpenFlags(): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeAllOpenFlags().map { it.map(::toDomain) }

    override fun observeAllFlags(): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeAll().map { list -> list.map { toDomain(it) } }

    override fun observeAcknowledgedFlagsByUser(userId: String): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeAcknowledgedFlagsByUser(userId).map { it.map(::toDomain) }

    override fun observeResolvedFlagsByPlant(plantId: String, sinceMillis: Long): Flow<List<BreakdownFlag>> =
        // Scope by plant is resolved in the ViewModel (asset-register flags have no
        // legacy `machines` row to join on); the DAO returns all resolved flags.
        db.breakdownFlagDao().observeResolvedFlags(sinceMillis).map { it.map(::toDomain) }

    override fun observeFlagById(flagId: String): Flow<BreakdownFlag?> =
        db.breakdownFlagDao().observeFlagById(flagId).map { it?.let(::toDomain) }

    override fun observeActiveFlagForMachine(machineId: String): Flow<BreakdownFlag?> =
        db.breakdownFlagDao().observeActiveFlagForMachine(machineId).map { it?.let(::toDomain) }

    override fun observeMachinesUnderBreakdown(): Flow<Set<String>> =
        db.breakdownFlagDao().observeActiveFlagMachineIds().map { it.toSet() }

    override fun observeMachinesUnderQc(): Flow<Set<String>> =
        db.breakdownFlagDao().observePendingQcMachineIds().map { it.toSet() }

    override fun observeLatestFlagForMachine(machineId: String): Flow<BreakdownFlag?> =
        db.breakdownFlagDao().observeLatestFlagForMachine(machineId).map { it?.let(::toDomain) }

    override fun observePendingQcByPlant(plantId: String): Flow<List<BreakdownFlag>> =
        // Scope by plant is resolved in the ViewModel; the DAO returns all
        // unassigned PENDING_QC flags (incl. asset-register ones).
        db.breakdownFlagDao().observePendingQc().map { it.map(::toDomain) }

    override fun observeQcAcknowledgedByUser(userId: String): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeQcAcknowledgedByUser(userId).map { it.map(::toDomain) }

    override fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeQcDoneByUserSince(userId, sinceMillis).map { it.map(::toDomain) }

    override suspend fun acknowledgeFlag(flagId: String, userId: String, userName: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            db.breakdownFlagDao().acknowledgeFlag(
                flagId = flagId,
                userId = userId,
                userName = userName,
                acknowledgedAt = now,
                syncStatus = SyncStatus.PENDING.name
            )
            // Best-effort push to backend (OPEN -> ACKNOWLEDGED); mark SYNCED on success.
            runCatching {
                breakdownApi.qcAcknowledge(
                    "Bearer ${authPreferences.getToken().orEmpty()}", flagId,
                    QcAcknowledgeRequestDto(userId = userId, userName = userName, acknowledgedAt = now)
                )
                db.breakdownFlagDao().updateSyncStatus(flagId, SyncStatus.SYNCED.name)
            }
            db.syncQueueDao().insert(
                SyncQueueEntity(
                    actionType = ActionType.BREAKDOWN_ACK.name,
                    payloadJson = """{"flag_id":"$flagId","user_id":"$userId"}""",
                    lastErrorMessage = null,
                    createdAt = now
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to acknowledge", e)
        }
    }

    override suspend fun resolveFlag(
        flagId: String, userId: String, userName: String,
        partsUsed: String, afterPhotoLocalPath: String?
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val machineId = db.breakdownFlagDao().getFlagByIdOnce(flagId)?.machineId
                ?: return Result.Error("Flag not found: $flagId")

            // Backend-authoritative (was a swallowed best-effort call): POST the repair
            // FIRST as multipart so the after-photo is uploaded to S3 and
            // work_done_des/photo_url are persisted server-side. If this throws (offline,
            // tunnel cold-start, 4xx) we DON'T advance local state — the failure surfaces
            // as Result.Error, the ticket stays ACKNOWLEDGED so "Mark Resolved" remains
            // for a retry, and the work-done data is never silently dropped.
            breakdownApi.workDone(
                "Bearer ${authPreferences.getToken().orEmpty()}", flagId,
                userId = userId.textPart(),
                userName = userName.textPart(),
                workDone = partsUsed.textPart(),
                doneAt = now.toString().textPart(),
                afterPhoto = photoPart("after_photo", afterPhotoLocalPath),
            )

            // Server accepted it -> commit local state (SYNCED) and hand the machine to QC.
            db.breakdownFlagDao().resolveFlag(
                flagId = flagId,
                userId = userId,
                userName = userName,
                resolvedAt = now,
                partsUsed = partsUsed,
                afterPhotoPath = afterPhotoLocalPath,
                syncStatus = SyncStatus.SYNCED.name
            )
            db.machineDao().updateStatus(machineId, MachineStatus.PENDING_QC.name, now)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                e.message ?: "Couldn't submit the repair to the server. Check your connection and try again.",
                e
            )
        }
    }

    override suspend fun qcAcknowledge(
        flagId: String,
        userId: String,
        userName: String,
        username: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val flag = db.breakdownFlagDao().getFlagByIdOnce(flagId)
                ?: return Result.Error("Ticket not found: $flagId")
            if (flag.qcAcknowledgedBy != null && flag.qcAcknowledgedBy != userId) {
                return Result.Error("Already picked up by ${flag.qcAcknowledgedByName ?: "another QC user"}")
            }
            db.breakdownFlagDao().qcAcknowledge(
                flagId = flagId,
                userId = userId,
                userName = userName,
                ackedAt = now,
                syncStatus = SyncStatus.PENDING.name
            )
            // Best-effort push to backend so mt_breakdown_records.qc_checked_by is set
            // immediately (not only after the sync worker drains the queue).
            runCatching {
                breakdownApi.qcAcknowledge(
                    "Bearer ${authPreferences.getToken().orEmpty()}", flagId,
                    QcAcknowledgeRequestDto(
                        userId = userId,
                        userName = userName,
                        qcCheckedBy = username,
                        acknowledgedAt = now
                    )
                )
                db.breakdownFlagDao().updateSyncStatus(flagId, SyncStatus.SYNCED.name)
            }
            db.syncQueueDao().insert(
                SyncQueueEntity(
                    actionType = ActionType.TICKET_QC_ACKNOWLEDGE.name,
                    payloadJson = """{"flag_id":"$flagId","user_id":"$userId","override":false}""",
                    lastErrorMessage = null,
                    createdAt = now
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to acknowledge for QC", e)
        }
    }

    override suspend fun qcOverrideAcknowledge(
        flagId: String, newUserId: String, newUserName: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            db.breakdownFlagDao().qcOverrideAcknowledge(
                flagId = flagId,
                newUserId = newUserId,
                newUserName = newUserName,
                ackedAt = now,
                syncStatus = SyncStatus.PENDING.name
            )
            db.syncQueueDao().insert(
                SyncQueueEntity(
                    actionType = ActionType.TICKET_QC_ACKNOWLEDGE.name,
                    payloadJson = """{"flag_id":"$flagId","user_id":"$newUserId","override":true}""",
                    lastErrorMessage = null,
                    createdAt = now
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to override QC acknowledgement", e)
        }
    }

    override suspend fun qcApprove(
        flagId: String, userId: String, userName: String,
        checklistJson: String, afterPhotoLocalPath: String?, notes: String?
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val machineId = db.breakdownFlagDao().getFlagByIdOnce(flagId)?.machineId
                ?: return Result.Error("Ticket not found: $flagId")
            // Backend-authoritative (was a swallowed best-effort call): persist
            // qc_status=APPROVED, qc_checked_by, end_time and status=CLOSED server-side
            // BEFORE committing locally. A failure surfaces as Result.Error instead of
            // leaving the columns NULL while the app shows "approved".
            breakdownApi.qcApprove(
                "Bearer ${authPreferences.getToken().orEmpty()}", flagId,
                QcDecisionRequestDto(
                    userId = userId, userName = userName, decidedAt = now,
                    checklistJson = checklistJson, afterPhotoPath = afterPhotoLocalPath, notes = notes
                )
            )
            db.breakdownFlagDao().qcApprove(
                flagId = flagId,
                userId = userId,
                userName = userName,
                decidedAt = now,
                notes = notes,
                photoPath = afterPhotoLocalPath,
                checklistJson = checklistJson,
                syncStatus = SyncStatus.SYNCED.name
            )
            db.machineDao().updateStatus(machineId, MachineStatus.IDLE.name, now)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                e.message ?: "Couldn't submit the QC approval to the server. Check your connection and try again.",
                e
            )
        }
    }

    override suspend fun qcDisapprove(
        flagId: String, userId: String, userName: String,
        checklistJson: String, afterPhotoLocalPath: String?, reason: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val machineId = db.breakdownFlagDao().getFlagByIdOnce(flagId)?.machineId
                ?: return Result.Error("Ticket not found: $flagId")
            // Backend-authoritative (was a swallowed best-effort call): persist
            // qc_status=REJECTED, qc_checked_by and qc_reject_reason server-side BEFORE
            // committing locally, so the rejection + reason are never NULL in the DB
            // while the app shows "disapproved".
            breakdownApi.qcDisapprove(
                "Bearer ${authPreferences.getToken().orEmpty()}", flagId,
                QcDecisionRequestDto(
                    userId = userId, userName = userName, decidedAt = now,
                    checklistJson = checklistJson, afterPhotoPath = afterPhotoLocalPath, reason = reason
                )
            )
            db.breakdownFlagDao().qcDisapprove(
                flagId = flagId,
                userId = userId,
                userName = userName,
                decidedAt = now,
                reason = reason,
                photoPath = afterPhotoLocalPath,
                checklistJson = checklistJson,
                syncStatus = SyncStatus.SYNCED.name
            )
            // Disapprove keeps the machine PENDING_QC so the operator stays blocked
            // and the technician sees a hot ticket again until QC finally approves.
            db.machineDao().updateStatus(machineId, MachineStatus.PENDING_QC.name, now)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                e.message ?: "Couldn't submit the QC decision to the server. Check your connection and try again.",
                e
            )
        }
    }

    override suspend fun getNewOpenFlagsSince(plantId: String, sinceMillis: Long): List<BreakdownFlag> =
        db.breakdownFlagDao().getNewOpenFlagsSince(plantId, sinceMillis).map(::toDomain)

    override suspend fun refreshOpenFlags(plantId: String): Result<Unit> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val open: List<OpenBreakdownDto> = breakdownApi.listOpen(token, plantId)
        val dao = db.breakdownFlagDao()
        for (dto in open) {
            val existing = dao.getFlagByIdOnce(dto.id)
            // Preserve a local edit that hasn't synced yet; otherwise mirror the server.
            if (existing != null && existing.syncStatus != SyncStatus.SYNCED.name) continue
            val base = existing ?: BreakdownFlagEntity(
                id = dto.id,
                machineId = dto.assetId,
                assetName = dto.assetName.ifBlank { null },
                building = dto.building,
                operatorId = dto.reportedBy ?: "",
                severity = dto.severity ?: "MAJOR",
                description = dto.description,
                beforePhotoLocalPath = null,
                raisedAt = dto.reportedAt ?: System.currentTimeMillis(),
                status = dto.status,
                acknowledgedBy = null,
                acknowledgedByName = null,
                acknowledgedAt = null,
                resolvedBy = null,
                resolvedByName = null,
                resolvedAt = null,
                partsUsed = null,
                afterPhotoLocalPath = null,
                qcStatus = dto.qcStatus,
                qcDecisionNotes = dto.qcRejectReason,
                syncStatus = SyncStatus.SYNCED.name
            )
            dao.insert(  // @Insert(onConflict = REPLACE) -> upsert by id
                base.copy(
                    machineId = dto.assetId,
                    assetName = dto.assetName.ifBlank { base.assetName },
                    building = dto.building ?: base.building,
                    status = dto.status,
                    severity = dto.severity ?: base.severity,
                    description = dto.description.ifBlank { base.description },
                    qcStatus = dto.qcStatus ?: base.qcStatus,
                    qcDecisionNotes = dto.qcRejectReason ?: base.qcDecisionNotes,
                    syncStatus = SyncStatus.SYNCED.name
                )
            )
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to refresh open breakdowns", e)
    }

    override fun getPendingBreakdowns(): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().getPendingFlags().map { list -> list.map(::toDomain) }

    override fun getSyncQueue(): Flow<List<SyncQueueItem>> =
        db.syncQueueDao().getAll().map { list ->
            list.map { SyncQueueItem(
                id = it.id, actionType = ActionType.valueOf(it.actionType),
                payloadJson = it.payloadJson, retryCount = it.retryCount,
                lastErrorMessage = it.lastErrorMessage, createdAt = it.createdAt
            )}
        }

    override suspend fun purgeMockSeedFlags() {
        // Real backend only: drop any sample flags left in Room by an earlier mock run.
        if (com.candorfoods.factoryops.BuildConfig.USE_MOCK_API) return
        db.breakdownFlagDao().deleteSeedFlags()
    }

    override suspend fun seedMockOpenFlagsIfEmpty() {
        // Breakdown/QC sample flags have been removed from MockSeedData entirely — there is nothing
        // to seed in either mode. Always purge any leftover "seed-flag-*" rows from earlier mock runs
        // so breakdowns/QC reflect the real backend table.
        db.breakdownFlagDao().deleteSeedFlags()
    }

    override suspend fun breakdownHistory(
        from: String,
        to: String,
        plantId: String,
        status: String?,
    ): Result<List<BreakdownFlag>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(breakdownApi.breakdownHistory(token, from, to, plantId, status).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load breakdown history", e)
        }
    }

    override suspend fun qcActivityHistory(
        from: String,
        to: String,
        plantId: String,
    ): Result<List<BreakdownFlag>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(breakdownApi.qcActivityHistory(token, from, to, plantId).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load QC activity history", e)
        }
    }

    // --- multipart helpers (S3 photo upload happens backend-side) ------------------

    private fun String.textPart(): RequestBody = toRequestBody(TEXT_PLAIN)

    /**
     * Build an optional image file part from a device-local path (the breakdown
     * photos are captured to app files via BreakdownPhotoStorage, so they're plain
     * File paths). Returns null when there's no path or the file is missing/empty —
     * the backend treats a null/absent part as "no photo".
     */
    private fun photoPart(formName: String, localPath: String?): MultipartBody.Part? {
        if (localPath.isNullOrBlank()) return null
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) return null
        val mime = if (file.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"
        val body = file.readBytes().toRequestBody(mime.toMediaType())
        return MultipartBody.Part.createFormData(formName, file.name, body)
    }

    private fun toDomain(e: BreakdownFlagEntity) = BreakdownFlag(
        id = e.id,
        machineId = e.machineId,
        assetName = e.assetName,
        building = e.building,
        operatorId = e.operatorId,
        severity = when (e.severity) {
            "CRITICAL" -> Severity.CRITICAL
            "MAJOR"    -> Severity.MAJOR
            else       -> Severity.MINOR
        },
        description = e.description,
        beforePhotoLocalPath = e.beforePhotoLocalPath,
        raisedAt = e.raisedAt,
        status = when (e.status) {
            "ACKNOWLEDGED" -> BreakdownStatus.ACKNOWLEDGED
            "PENDING_QC"   -> BreakdownStatus.PENDING_QC
            "CLOSED"       -> BreakdownStatus.CLOSED
            "REOPENED"     -> BreakdownStatus.REOPENED
            else           -> BreakdownStatus.OPEN
        },
        acknowledgedBy = e.acknowledgedBy,
        acknowledgedByName = e.acknowledgedByName,
        acknowledgedAt = e.acknowledgedAt,
        resolvedBy = e.resolvedBy,
        resolvedByName = e.resolvedByName,
        resolvedAt = e.resolvedAt,
        partsUsed = e.partsUsed,
        afterPhotoLocalPath = e.afterPhotoLocalPath,
        qcStatus = when (e.qcStatus) {
            "PENDING"      -> QcStatus.PENDING
            "APPROVED"     -> QcStatus.APPROVED
            // Backend's redesigned QC flow emits "DISAPPROVED"; older builds emitted
            // "REJECTED". Map both to the same domain value.
            "REJECTED", "DISAPPROVED" -> QcStatus.REJECTED
            else           -> null
        },
        qcAcknowledgedBy = e.qcAcknowledgedBy,
        qcAcknowledgedByName = e.qcAcknowledgedByName,
        qcAcknowledgedAt = e.qcAcknowledgedAt,
        qcDecidedBy = e.qcDecidedBy,
        qcDecidedByName = e.qcDecidedByName,
        qcDecidedAt = e.qcDecidedAt,
        qcDecisionNotes = e.qcDecisionNotes,
        qcAfterPhotoLocalPath = e.qcAfterPhotoLocalPath,
        qcChecklistJson = e.qcChecklistJson,
        qcOverrideFromUserId = e.qcOverrideFromUserId,
        qcOverrideFromName = e.qcOverrideFromName,
        qcOverrideAt = e.qcOverrideAt,
        syncStatus = when (e.syncStatus) {
            "SYNCED"  -> SyncStatus.SYNCED
            "FAILED"  -> SyncStatus.FAILED
            "SYNCING" -> SyncStatus.SYNCING
            else      -> SyncStatus.PENDING
        }
    )

    private companion object {
        private val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
