package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.entity.PmWoSpareEntity
import com.candorfoods.factoryops.data.local.entity.PmWoTaskLogEntity
import com.candorfoods.factoryops.data.local.entity.PmWorkOrderEntity
import com.candorfoods.factoryops.data.local.entity.SyncQueueEntity
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
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PmWorkOrderRepositoryImpl @Inject constructor(
    private val db: AppDatabase
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
        enqueueSync(ActionType.PM_ACKNOWLEDGE, """{"wo_id":"$woId"}""")
    }

    override suspend fun start(woId: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markStarted(woId, now)
        woDao.updateEscalationTier(woId, 0)
        enqueueSync(ActionType.PM_START, """{"wo_id":"$woId"}""")
    }

    override suspend fun logTaskResult(
        logId: String,
        woId: String,
        status: PmTaskLogStatus,
        measurementValue: Double?,
        photoLocalPath: String?,
        notes: String?,
        userId: String
    ): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        logDao.updateLogResult(
            id = logId,
            status = status.name,
            measurementValue = measurementValue,
            photoLocalPath = photoLocalPath,
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
        spareName: String,
        quantityUsed: Double,
        unit: String,
        notes: String?,
        userId: String
    ): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        val spareId = "spare-${UUID.randomUUID()}"
        spareDao.upsert(
            PmWoSpareEntity(
                id = spareId,
                woId = woId,
                spareName = spareName.trim(),
                quantityUsed = quantityUsed,
                unit = unit.trim(),
                notes = notes,
                loggedAt = now,
                loggedBy = userId,
                syncStatus = "PENDING"
            )
        )
        enqueueSync(ActionType.PM_LOG_SPARE, """{"wo_id":"$woId","spare_id":"$spareId"}""")
    }

    override suspend fun deleteSpare(spareId: String, woId: String): Result<Unit> = mutate {
        spareDao.deleteById(spareId)
        // No sync action needed — server will reconcile by snapshot when Phase 3 ships.
    }

    override suspend fun submit(woId: String, finalNotes: String?): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markSubmitted(woId, now, finalNotes)
        woDao.updateEscalationTier(woId, 0)
        enqueueSync(ActionType.PM_SUBMIT, """{"wo_id":"$woId"}""")
    }

    override suspend fun supervisorApprove(woId: String, supervisorId: String, supervisorName: String): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        woDao.markSupervisorApproved(woId, supervisorId, supervisorName, now)
        // Phase 2C-3: handoff flips the machine to PENDING_QC so it shows up
        // as "in QC review" on the operator/machine list while QC walks it.
        db.machineDao().updateStatus(wo.machineId, MachineStatus.PENDING_QC.name, now)
        enqueueSync(ActionType.PM_SUPERVISOR_APPROVE, """{"wo_id":"$woId"}""")
    }

    override suspend fun supervisorReject(
        woId: String,
        supervisorId: String,
        supervisorName: String,
        notes: String
    ): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        val safeNotes = notes.replace("\"", "\\\"")
        woDao.markSupervisorRejected(woId, now, notes)
        // If a prior approve cycle had flipped the machine to PENDING_QC, return
        // it to IDLE now that the WO is back in IN_PROGRESS for tech rework.
        db.machineDao().updateStatus(wo.machineId, MachineStatus.IDLE.name, now)
        enqueueSync(
            ActionType.PM_SUPERVISOR_REJECT,
            """{"wo_id":"$woId","reason":"$safeNotes"}"""
        )
    }

    override suspend fun suggestSpareNames(query: String, limit: Int): List<String> =
        spareDao.suggestSpareNames(query.trim(), limit)

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
        enqueueSync(
            ActionType.PM_QC_ACKNOWLEDGE,
            """{"wo_id":"$woId","user_id":"$userId","override":false}"""
        )
    }

    override suspend fun qcOverrideAcknowledge(woId: String, userId: String, userName: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markQcOverrideAcknowledged(woId, userId, userName, now)
        enqueueSync(
            ActionType.PM_QC_ACKNOWLEDGE,
            """{"wo_id":"$woId","user_id":"$userId","override":true}"""
        )
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
        enqueueSync(ActionType.PM_QC_APPROVE, """{"wo_id":"$woId","user_id":"$userId"}""")
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
        val safeReason = reason.replace("\"", "\\\"")
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
        enqueueSync(
            ActionType.PM_QC_DISAPPROVE,
            """{"wo_id":"$woId","user_id":"$userId","reason":"$safeReason"}"""
        )
    }

    // ─── Phase 2C-4 — escalation tier ─────────────────────────────────────────

    override suspend fun bumpTier(woId: String, newTier: Int): Result<Unit> = mutate {
        woDao.updateEscalationTier(woId, newTier)
        // Q9A: fire-and-forget. No sync queue entry — tier is a UI urgency signal,
        // not an audit-worthy state change. Phase 3 backend will compute server-side.
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
        expectedResult = expectedResult,
        requiresPhoto = requiresPhoto,
        requiresMeasurement = requiresMeasurement,
        measurementUnit = measurementUnit,
        measurementMin = measurementMin,
        measurementMax = measurementMax,
        status = runCatching { PmTaskLogStatus.valueOf(status) }.getOrDefault(PmTaskLogStatus.PENDING),
        measurementValue = measurementValue,
        photoLocalPath = photoLocalPath,
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
        expectedResult = expectedResult,
        requiresPhoto = requiresPhoto,
        requiresMeasurement = requiresMeasurement,
        measurementUnit = measurementUnit,
        measurementMin = measurementMin,
        measurementMax = measurementMax,
        status = status.name,
        measurementValue = measurementValue,
        photoLocalPath = photoLocalPath,
        notes = notes,
        completedAt = completedAt,
        completedBy = completedBy,
        syncStatus = SyncStatus.PENDING.name
    )

    private fun PmWoSpareEntity.toDomain() = PmWoSpare(
        id = id,
        woId = woId,
        spareName = spareName,
        quantityUsed = quantityUsed,
        unit = unit,
        notes = notes,
        loggedAt = loggedAt,
        loggedBy = loggedBy
    )
}
