package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.PmWoSpareEntity
import com.candorfoods.factoryops.data.local.entity.PmWoTaskLogEntity
import com.candorfoods.factoryops.data.local.entity.PmWorkOrderEntity
import com.candorfoods.factoryops.data.local.entity.SyncQueueEntity
import com.candorfoods.factoryops.data.remote.api.PmSyncApi
import com.candorfoods.factoryops.data.remote.dto.PmAtRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmQcAckRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmQcDecisionRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmSubmitRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmSupervisorApproveRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmSupervisorRejectRequestDto
import com.candorfoods.factoryops.data.remote.dto.PmWorkOrderDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.data.remote.dto.toDto
import com.candorfoods.factoryops.domain.model.ActionType
import com.candorfoods.factoryops.domain.model.MachineStatus
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoSpare
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWoTaskLog
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.model.spareparts.MAX_WO_SPARES
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PmWorkOrderRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val api: PmSyncApi,
    private val authPreferences: AuthPreferences,
    private val sparePartsRepo: SparePartsRepository,
) : PmWorkOrderRepository {

    private val woDao    get() = db.pmWorkOrderDao()
    private val logDao   get() = db.pmWoTaskLogDao()
    private val spareDao get() = db.pmWoSpareDao()
    private val syncDao  get() = db.syncQueueDao()

    override fun observeAll(): Flow<List<PmWorkOrder>> =
        woDao.observeAll().map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeForTechnician(techId: String, statuses: List<PmWoStatus>): Flow<List<PmWorkOrder>> =
        woDao.observeForTechnician(techId, statuses.map { it.name })
            .map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeByStatus(status: PmWoStatus): Flow<List<PmWorkOrder>> =
        woDao.observeByStatus(status.name).map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeById(id: String): Flow<PmWorkOrder?> =
        combine(
            woDao.observeById(id),
            logDao.observeForWo(id),
            spareDao.observeForWo(id)
        ) { wo, logs, spares ->
            wo?.toDomain(logs.map { it.toDomain() }, spares.map { it.toDomain() })
        }

    override suspend fun getById(id: String): PmWorkOrder? {
        val wo = woDao.getById(id) ?: return null
        val logs = logDao.getForWo(id).map { it.toDomain() }
        val spares = spareDao.getForWo(id).map { it.toDomain() }
        return wo.toDomain(logs, spares)
    }

    override suspend fun findOpenForPlan(planId: String): List<PmWorkOrder> {
        val openStatuses = listOf(
            PmWoStatus.DRAFT.name,
            PmWoStatus.NOTIFIED.name,
            PmWoStatus.ACKNOWLEDGED.name,
            PmWoStatus.IN_PROGRESS.name,
            PmWoStatus.SUBMITTED.name,
            PmWoStatus.SUPERVISOR_APPROVED.name,
            PmWoStatus.PENDING_QC.name
        )
        return woDao.findOpenForPlan(planId, openStatuses)
            .map { it.toDomain(emptyList(), emptyList()) }
    }

    override suspend fun getAllNonClosed(): List<PmWorkOrder> =
        woDao.getAllNonClosed().map { it.toDomain(emptyList(), emptyList()) }

    override suspend fun refreshForTechnician(techId: String): Result<Unit> = try {
        cacheServerWos(api.listWorkOrders(bearer(), technicianId = techId))
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to refresh PM work orders", e)
    }

    override suspend fun refreshByStatuses(statuses: List<PmWoStatus>): Result<Unit> = try {
        if (statuses.isNotEmpty()) {
            cacheServerWos(api.listWorkOrders(bearer(), status = statuses.joinToString(",") { it.name }))
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to refresh PM work orders", e)
    }

    override suspend fun qcClosedHistory(): Result<List<PmWorkOrder>> = try {
        // Network-only (bypasses Room): the QC archive is org-wide and all-time, not the
        // device's local slice. CLOSED = QC-approved for a work order.
        Result.Success(api.listWorkOrders(bearer(), status = PmWoStatus.CLOSED.name).map { it.toDomain() })
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load QC history", e)
    }

    override suspend fun refreshWorkOrder(id: String): Result<Unit> = try {
        cacheServerWos(listOf(api.getWorkOrder(bearer(), id)))
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to refresh PM work order", e)
    }

    /** Mirror server rows into Room as SYNCED (they came FROM the server — don't re-push). */
    private suspend fun cacheServerWos(dtos: List<PmWorkOrderDto>) {
        for (dto in dtos) {
            val wo = dto.toDomain()
            // escalation_tier isn't carried by the backend, so toDomain() reads it back as
            // 0. Keep whatever this device already computed: overwriting it made every
            // refresh look like a fresh tier increase, which re-notified the technician
            // about the same work order every single hour.
            val tier = woDao.getById(wo.id)?.escalationTier ?: wo.escalationTier
            woDao.upsert(wo.toEntity().copy(syncStatus = SyncStatus.SYNCED.name, escalationTier = tier))
            if (wo.taskLogs.isNotEmpty()) {
                logDao.upsertAll(wo.taskLogs.map { it.toEntity().copy(syncStatus = SyncStatus.SYNCED.name) })
            }
        }
    }

    override suspend fun insertGeneratedWo(wo: PmWorkOrder): Result<PmWorkOrder> = try {
        woDao.upsert(wo.toEntity())
        if (wo.taskLogs.isNotEmpty()) {
            logDao.upsertAll(wo.taskLogs.map { it.toEntity() })
        }
        Result.Success(wo)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Insert failed", e)
    }

    override suspend fun updateStatusAndTier(id: String, status: PmWoStatus, tier: Int) {
        woDao.updateStatusAndTier(id, status.name, tier)
    }

    override suspend fun updateEscalationTier(id: String, tier: Int) {
        woDao.updateEscalationTier(id, tier)
    }

    // ─── Phase 2C-2 — execution + supervisor approval actions ─────────────────

    override suspend fun acknowledge(woId: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markAcknowledged(woId, now)
        woDao.updateEscalationTier(woId, 0)
        // Best-effort push (mt_pm_work_order -> ACKNOWLEDGED); reconcile from the response.
        runCatching { applyServerWo(api.acknowledgeWorkOrder(bearer(), woId, PmAtRequestDto(at = now))) }
    }

    override suspend fun start(woId: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markStarted(woId, now)
        woDao.updateEscalationTier(woId, 0)
        runCatching { applyServerWo(api.startWorkOrder(bearer(), woId, PmAtRequestDto(at = now))) }
    }

    override suspend fun logTaskResult(
        logId: String,
        woId: String,
        status: PmTaskLogStatus,
        notes: String?,
        userId: String
    ): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        logDao.updateLogResult(
            id = logId,
            status = status.name,
            notes = notes,
            ts = now,
            userId = userId
        )
        // Touch parent WO so it transitions out of NOTIFIED/ACKNOWLEDGED on first item completion.
        woDao.markStarted(woId, now)
        enqueueSync(ActionType.PM_LOG_ITEM, """{"wo_id":"$woId","log_id":"$logId"}""")
    }

    override suspend fun logSpare(
        woId: String,
        pick: SparePartPick,
        quantityUsed: Double,
        notes: String?,
        userId: String
    ): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        val existing = spareDao.getForWo(woId)

        // Picking a part that is already on the list adds to that line instead of
        // creating a second one — two lines for one part would still deduct the
        // right total server-side, but read as a mistake on every review screen.
        val duplicate = existing.firstOrNull { it.partId == pick.partId }
        if (duplicate != null) {
            spareDao.upsert(
                duplicate.copy(
                    quantityUsed = duplicate.quantityUsed + quantityUsed,
                    notes = notes ?: duplicate.notes,
                    syncStatus = "PENDING",
                )
            )
            enqueueSync(ActionType.PM_LOG_SPARE, """{"wo_id":"$woId","spare_id":"${duplicate.id}"}""")
            return@mutate
        }

        require(existing.size < MAX_WO_SPARES) {
            "You can log at most $MAX_WO_SPARES spare parts on one PM."
        }

        val spareId = "spare-${UUID.randomUUID()}"
        spareDao.upsert(
            PmWoSpareEntity(
                id = spareId,
                woId = woId,
                partId = pick.partId,
                spareName = pick.partName.trim(),
                machineName = pick.machineName,
                quantityUsed = quantityUsed,
                unit = pick.unit.orEmpty().trim(),
                notes = notes,
                loggedAt = now,
                loggedBy = userId,
                syncStatus = "PENDING"
            )
        )
        enqueueSync(ActionType.PM_LOG_SPARE, """{"wo_id":"$woId","spare_id":"$spareId"}""")
    }

    override suspend fun sparePicker(assetId: String?): SparePartPickerCatalog =
        sparePartsRepo.picker(assetId)

    override suspend fun deleteSpare(spareId: String, woId: String): Result<Unit> = mutate {
        spareDao.deleteById(spareId)
        // No sync action needed — server will reconcile by snapshot when Phase 3 ships.
    }

    override suspend fun submit(woId: String, finalNotes: String?, overallResult: String?): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        // Gather the technician's completed checklist (status = Is-good verdict + remark) + spares.
        val logDtos = logDao.getForWo(woId).map { it.toDomain().toDto() }
        val spareDtos = spareDao.getForWo(woId).map { it.toDomain().toDto() }
        // Backend-authoritative: the checklist MUST land in mt_pm_work_order before we advance
        // local state, so a failure surfaces (the ticket stays executable to retry).
        val dto = api.submitWorkOrder(
            bearer(), woId,
            PmSubmitRequestDto(
                finalNotes = finalNotes.orEmpty(),
                overallResult = overallResult.orEmpty(),
                at = now,
                taskLogs = logDtos,
                spares = spareDtos,
            ),
        )
        woDao.markSubmitted(woId, now, finalNotes, overallResult)
        woDao.updateEscalationTier(woId, 0)
        applyServerWo(dto)
    }

    override suspend fun supervisorApprove(woId: String, supervisorId: String, supervisorName: String): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        // Backend-authoritative: persist the approval (-> PENDING_QC) before committing local.
        val dto = api.supervisorApproveWorkOrder(
            bearer(), woId,
            PmSupervisorApproveRequestDto(supervisorId = supervisorId, supervisorName = supervisorName, at = now),
        )
        woDao.markSupervisorApproved(woId, supervisorId, supervisorName, now)
        // Handoff flips the machine to PENDING_QC so it shows as "in QC review".
        db.machineDao().updateStatus(wo.machineId, MachineStatus.PENDING_QC.name, now)
        applyServerWo(dto)
    }

    override suspend fun supervisorReject(
        woId: String,
        supervisorId: String,
        supervisorName: String,
        notes: String
    ): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        val dto = api.supervisorRejectWorkOrder(
            bearer(), woId,
            PmSupervisorRejectRequestDto(supervisorId = supervisorId, supervisorName = supervisorName, notes = notes, at = now),
        )
        woDao.markSupervisorRejected(woId, now, notes)
        // Back to IN_PROGRESS for tech rework; return the machine to IDLE.
        db.machineDao().updateStatus(wo.machineId, MachineStatus.IDLE.name, now)
        applyServerWo(dto)
    }

    // ─── Phase 2C-3 — QC inbox observers ──────────────────────────────────────

    override fun observePendingQc(): Flow<List<PmWorkOrder>> =
        woDao.observePendingQc().map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeQcAcknowledgedByUser(userId: String): Flow<List<PmWorkOrder>> =
        woDao.observeQcAcknowledgedByUser(userId).map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<PmWorkOrder>> =
        woDao.observeQcDoneByUserSince(userId, sinceMillis).map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    // ─── Phase 2C-3 — QC actions ──────────────────────────────────────────────

    override suspend fun qcAcknowledge(woId: String, userId: String, userName: String): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        if (wo.qcAcknowledgedBy != null && wo.qcAcknowledgedBy != userId) {
            throw IllegalStateException("Already picked up by ${wo.qcAcknowledgedByName ?: "another QC user"}")
        }
        val now = System.currentTimeMillis()
        woDao.markQcAcknowledged(woId, userId, userName, now)
        runCatching {
            applyServerWo(api.qcAcknowledgeWorkOrder(bearer(), woId, PmQcAckRequestDto(userId = userId, userName = userName, at = now)))
        }
    }

    override suspend fun qcOverrideAcknowledge(woId: String, userId: String, userName: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markQcOverrideAcknowledged(woId, userId, userName, now)
        runCatching {
            applyServerWo(api.qcOverrideAcknowledgeWorkOrder(bearer(), woId, PmQcAckRequestDto(userId = userId, userName = userName, at = now)))
        }
    }

    override suspend fun qcApprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        notes: String?
    ): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        val afterUrl = uploadIfLocal(afterPhotoLocalPath)
        // Backend-authoritative: persist qc_checklist + CLOSED (this advances the plan's
        // schedule for the next cycle) before committing local.
        val dto = api.qcApproveWorkOrder(
            bearer(), woId,
            PmQcDecisionRequestDto(
                userId = userId, userName = userName,
                checklist = qcBlob(checklistJson, afterUrl, notes), notes = notes.orEmpty(), at = now,
            ),
        )
        woDao.markQcApproved(
            id = woId,
            userId = userId,
            userName = userName,
            ts = now,
            notes = notes,
            photoPath = afterPhotoLocalPath,
            checklistJson = checklistJson
        )
        // Machine returns to operator-controllable state.
        db.machineDao().updateStatus(wo.machineId, MachineStatus.IDLE.name, now)
        applyServerWo(dto)
    }

    override suspend fun qcDisapprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        reason: String
    ): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        val afterUrl = uploadIfLocal(afterPhotoLocalPath)
        val dto = api.qcDisapproveWorkOrder(
            bearer(), woId,
            PmQcDecisionRequestDto(
                userId = userId, userName = userName,
                checklist = qcBlob(checklistJson, afterUrl, reason), notes = reason, at = now,
            ),
        )
        woDao.markQcDisapproved(
            id = woId,
            userId = userId,
            userName = userName,
            ts = now,
            reason = reason,
            photoPath = afterPhotoLocalPath,
            checklistJson = checklistJson
        )
        // Machine stays on PENDING_QC — the supervisor still has it under review.
        applyServerWo(dto)
    }

    // ─── Phase 2C-4 — escalation tier ─────────────────────────────────────────

    override suspend fun bumpTier(woId: String, newTier: Int): Result<Unit> = mutate {
        woDao.updateEscalationTier(woId, newTier)
        // Q9A: fire-and-forget. No sync queue entry — tier is a UI urgency signal,
        // not an audit-worthy state change. Phase 3 backend will compute server-side.
    }

    override suspend fun history(status: String?): Result<List<PmWorkOrder>> {
        return try {
            Result.Success(api.listWorkOrders(bearer(), technicianId = null, status = status).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load PM history", e)
        }
    }

    // Helper — wraps an action body in try/catch, returning Result<Unit>.
    private suspend inline fun mutate(crossinline body: suspend () -> Unit): Result<Unit> = try {
        body()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Action failed", e)
    }

    private suspend fun enqueueSync(actionType: ActionType, payloadJson: String) {
        syncDao.insert(
            SyncQueueEntity(
                actionType = actionType.name,
                payloadJson = payloadJson,
                lastErrorMessage = null,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // ─── Backend write-back helpers (POST /pm/work-orders/{id}/…) ──────────────

    private fun bearer() = "Bearer ${authPreferences.getToken().orEmpty()}"

    /** Reconcile the local WO row to the server's returned truth after a lifecycle call.
     *  Only the WO row is touched — task_logs/spares stay local so device photo paths and
     *  in-progress edits aren't clobbered by the server's URLs. */
    private suspend fun applyServerWo(dto: PmWorkOrderDto) {
        woDao.upsert(dto.toDomain().toEntity().copy(syncStatus = SyncStatus.SYNCED.name))
    }

    /** Upload a device-local image to S3 (POST /pm/photos) and return its URL. An existing
     *  http(s) URL passes through unchanged; a null/missing/empty file → null. */
    private suspend fun uploadIfLocal(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http", ignoreCase = true)) return path
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val mime = if (file.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"
        val part = MultipartBody.Part.createFormData(
            "file", file.name, file.readBytes().toRequestBody(mime.toMediaType())
        )
        return api.uploadPmPhoto(bearer(), part).url
    }

    /** QC sign-off blob stored verbatim in qc_checklist: the app's checklist JSON + any
     *  after-photo URL + notes. Null when there's nothing to record. */
    private fun qcBlob(checklistJson: String?, afterUrl: String?, notes: String?): JsonElement? {
        val checklist = checklistJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        if (checklist == null && afterUrl == null && notes.isNullOrBlank()) return null
        return buildJsonObject {
            checklist?.let { put("checklist", it) }
            afterUrl?.let { put("after_photo_url", it) }
            if (!notes.isNullOrBlank()) put("notes", notes)
        }
    }

    private fun PmWorkOrderEntity.toDomain(logs: List<PmWoTaskLog>, spares: List<PmWoSpare>) = PmWorkOrder(
        id = id,
        planId = planId,
        machineId = machineId,
        templateId = templateId,
        templateName = templateName,
        machineType = runCatching { MachineType.valueOf(machineType) }.getOrDefault(MachineType.OTHER),
        estimatedDurationMinutes = estimatedDurationMinutes,
        tradeType = runCatching { TradeType.valueOf(tradeType) }.getOrDefault(TradeType.MECHANICAL),
        scheduledDate = scheduledDate,
        generatedAt = generatedAt,
        status = runCatching { PmWoStatus.valueOf(status) }.getOrDefault(PmWoStatus.DRAFT),
        assignedTechnicianId = assignedTechnicianId,
        assignedTechnicianName = assignedTechnicianName,
        acknowledgedAt = acknowledgedAt,
        startedAt = startedAt,
        submittedAt = submittedAt,
        supervisorApprovedBy = supervisorApprovedBy,
        supervisorApprovedByName = supervisorApprovedByName,
        supervisorApprovedAt = supervisorApprovedAt,
        supervisorRejectedAt = supervisorRejectedAt,
        supervisorRejectionNotes = supervisorRejectionNotes,
        qcAcknowledgedBy = qcAcknowledgedBy,
        qcAcknowledgedByName = qcAcknowledgedByName,
        qcAcknowledgedAt = qcAcknowledgedAt,
        qcDecidedBy = qcDecidedBy,
        qcDecidedByName = qcDecidedByName,
        qcDecidedAt = qcDecidedAt,
        qcDecisionNotes = qcDecisionNotes,
        qcAfterPhotoLocalPath = qcAfterPhotoLocalPath,
        qcChecklistJson = qcChecklistJson,
        closedAt = closedAt,
        finalNotes = finalNotes,
        overallResult = overallResult,
        escalationTier = escalationTier,
        taskLogs = logs,
        spares = spares
    )

    private fun PmWorkOrder.toEntity() = PmWorkOrderEntity(
        id = id,
        planId = planId,
        machineId = machineId,
        templateId = templateId,
        templateName = templateName,
        machineType = machineType.name,
        estimatedDurationMinutes = estimatedDurationMinutes,
        tradeType = tradeType.name,
        scheduledDate = scheduledDate,
        generatedAt = generatedAt,
        status = status.name,
        assignedTechnicianId = assignedTechnicianId,
        assignedTechnicianName = assignedTechnicianName,
        acknowledgedAt = acknowledgedAt,
        startedAt = startedAt,
        submittedAt = submittedAt,
        supervisorApprovedBy = supervisorApprovedBy,
        supervisorApprovedByName = supervisorApprovedByName,
        supervisorApprovedAt = supervisorApprovedAt,
        supervisorRejectedAt = supervisorRejectedAt,
        supervisorRejectionNotes = supervisorRejectionNotes,
        qcAcknowledgedBy = qcAcknowledgedBy,
        qcAcknowledgedByName = qcAcknowledgedByName,
        qcAcknowledgedAt = qcAcknowledgedAt,
        qcDecidedBy = qcDecidedBy,
        qcDecidedByName = qcDecidedByName,
        qcDecidedAt = qcDecidedAt,
        qcDecisionNotes = qcDecisionNotes,
        qcAfterPhotoLocalPath = qcAfterPhotoLocalPath,
        qcChecklistJson = qcChecklistJson,
        closedAt = closedAt,
        finalNotes = finalNotes,
        overallResult = overallResult,
        escalationTier = escalationTier,
        syncStatus = SyncStatus.PENDING.name
    )

    private fun PmWoTaskLogEntity.toDomain() = PmWoTaskLog(
        id = id,
        woId = woId,
        templateItemId = templateItemId,
        orderIndex = orderIndex,
        title = title,
        description = description,
        status = runCatching { PmTaskLogStatus.valueOf(status) }.getOrDefault(PmTaskLogStatus.PENDING),
        notes = notes,
        completedAt = completedAt,
        completedBy = completedBy
    )

    private fun PmWoTaskLog.toEntity() = PmWoTaskLogEntity(
        id = id,
        woId = woId,
        templateItemId = templateItemId,
        orderIndex = orderIndex,
        title = title,
        description = description,
        status = status.name,
        notes = notes,
        completedAt = completedAt,
        completedBy = completedBy,
        syncStatus = SyncStatus.PENDING.name
    )

    private fun PmWoSpareEntity.toDomain() = PmWoSpare(
        id = id,
        woId = woId,
        partId = partId,
        spareName = spareName,
        machineName = machineName,
        quantityUsed = quantityUsed,
        unit = unit,
        notes = notes,
        loggedAt = loggedAt,
        loggedBy = loggedBy
    )
}
