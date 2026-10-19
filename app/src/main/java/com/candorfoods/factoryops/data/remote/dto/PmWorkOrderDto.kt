package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWoSpare
import com.candorfoods.factoryops.domain.model.PmWoTaskLog
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.TradeType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Wire model for the backend `mt_pm_work_order` table (two-table design: task logs
 * and spares are JSONB arrays here, no child tables). The app's [PmWorkOrder] carries
 * a few extra fields that the backend dropped (machineType, tradeType, the qc-decision
 * columns, escalationTier); those are filled with defaults on read and skipped on write.
 */
@Serializable
data class PmWorkOrderDto(
    val id: String,
    @SerialName("plan_id")                     val planId: String,
    @SerialName("machine_id")                  val machineId: String,
    @SerialName("machine_name")                val machineName: String = "",
    @SerialName("template_name")               val templateName: String = "",
    @SerialName("estimated_duration_minutes")  val estimatedDurationMinutes: Int = 0,
    @SerialName("scheduled_date")              val scheduledDate: Long,
    @SerialName("generated_at")                val generatedAt: Long,
    val status: String = "NOTIFIED",
    @SerialName("assigned_technician_id")      val assignedTechnicianId: String,
    @SerialName("assigned_technician_name")    val assignedTechnicianName: String? = null,
    @SerialName("acknowledged_at")             val acknowledgedAt: Long? = null,
    @SerialName("started_at")                  val startedAt: Long? = null,
    @SerialName("submitted_at")                val submittedAt: Long? = null,
    @SerialName("final_notes")                 val finalNotes: String? = null,
    @SerialName("overall_result")              val overallResult: String? = null,
    @SerialName("supervisor_approved_by")      val supervisorApprovedBy: String? = null,
    @SerialName("supervisor_approved_by_name") val supervisorApprovedByName: String? = null,
    @SerialName("supervisor_approved_at")      val supervisorApprovedAt: Long? = null,
    @SerialName("supervisor_rejected_at")      val supervisorRejectedAt: Long? = null,
    @SerialName("supervisor_rejection_notes")  val supervisorRejectionNotes: String? = null,
    @SerialName("qc_acknowledged_by")          val qcAcknowledgedBy: String? = null,
    @SerialName("qc_acknowledged_by_name")     val qcAcknowledgedByName: String? = null,
    @SerialName("qc_acknowledged_at")          val qcAcknowledgedAt: Long? = null,
    @SerialName("qc_checklist")                val qcChecklist: JsonElement? = null,
    @SerialName("closed_at")                   val closedAt: Long? = null,
    @SerialName("task_logs")                   val taskLogs: List<PmTaskLogDto> = emptyList(),
    val spares: List<PmSpareDto> = emptyList(),
    @SerialName("created_at")                  val createdAt: Long = 0,
    @SerialName("updated_at")                  val updatedAt: Long = 0,
)

@Serializable
data class PmTaskLogDto(
    val id: String,
    @SerialName("template_item_id")     val templateItemId: String? = null,
    @SerialName("order_index")          val orderIndex: Int = 0,
    val title: String = "",
    val description: String = "",           // "Remarks" guidance snapshot
    val status: String = "PENDING",         // PENDING | PASS (Is good) | FAIL (not good)
    val notes: String? = null,              // technician remark; required when FAIL
    @SerialName("completed_at")         val completedAt: Long? = null,
    @SerialName("completed_by")         val completedBy: String? = null,
)

@Serializable
data class PmSpareDto(
    val id: String,
    // The mt_spareparts row this line decrements. Null only on entries logged
    // before the picker shipped — those are free text and move no stock.
    @SerialName("part_id")       val partId: Int? = null,
    @SerialName("spare_name")    val spareName: String,
    @SerialName("machine_name")  val machineName: String? = null,
    @SerialName("quantity_used") val quantityUsed: Double,
    val unit: String = "",
    val notes: String? = null,
    @SerialName("logged_at")     val loggedAt: Long = 0,
    @SerialName("logged_by")     val loggedBy: String = "",
    // Server's word on whether stock has already been moved for this line. Sent
    // back verbatim on a resubmit; the server ignores it and trusts its own copy.
    @SerialName("deducted")      val deducted: Boolean = false,
)

// ─── Read: backend WO → app PmWorkOrder (dropped fields → defaults) ──────────

fun PmWorkOrderDto.toDomain(): PmWorkOrder = PmWorkOrder(
    id = id,
    planId = planId,
    machineId = machineId,
    templateId = "",                                 // app-only link; unused post-merge
    templateName = templateName.ifBlank { machineName },
    machineType = MachineType.OTHER,                 // dropped on backend → default
    estimatedDurationMinutes = estimatedDurationMinutes,
    tradeType = TradeType.MECHANICAL,                // dropped on backend → default
    scheduledDate = scheduledDate,
    generatedAt = generatedAt,
    status = runCatching { PmWoStatus.valueOf(status) }.getOrDefault(PmWoStatus.NOTIFIED),
    assignedTechnicianId = assignedTechnicianId,
    assignedTechnicianName = assignedTechnicianName.orEmpty(),
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
    qcDecidedBy = null,                              // dropped on backend
    qcDecidedByName = null,
    qcDecidedAt = null,
    qcDecisionNotes = null,
    qcAfterPhotoLocalPath = null,                    // dropped on backend
    qcChecklistJson = qcChecklist?.toString(),
    closedAt = closedAt,
    finalNotes = finalNotes,
    overallResult = overallResult,
    escalationTier = 0,                              // dropped on backend
    taskLogs = taskLogs.map { it.toDomain(id) },
    spares = spares.map { it.toDomain(id) },
)

fun PmTaskLogDto.toDomain(woId: String) = PmWoTaskLog(
    id = id,
    woId = woId,
    templateItemId = templateItemId.orEmpty(),
    orderIndex = orderIndex,
    title = title,
    description = description,
    status = runCatching { PmTaskLogStatus.valueOf(status) }.getOrDefault(PmTaskLogStatus.PENDING),
    notes = notes,
    completedAt = completedAt,
    completedBy = completedBy,
)

fun PmSpareDto.toDomain(woId: String) = PmWoSpare(
    id = id,
    woId = woId,
    partId = partId,
    spareName = spareName,
    machineName = machineName,
    quantityUsed = quantityUsed,
    unit = unit,
    notes = notes,
    loggedAt = loggedAt,
    loggedBy = loggedBy,
)

// ─── Write: app PmWorkOrder → backend WO payload (skip app-only fields) ──────

fun PmWorkOrder.toDto(): PmWorkOrderDto = PmWorkOrderDto(
    id = id,
    planId = planId,
    machineId = machineId,
    machineName = templateName,                      // merged model: templateName = asset name
    templateName = templateName,
    estimatedDurationMinutes = estimatedDurationMinutes,
    scheduledDate = scheduledDate,
    generatedAt = generatedAt,
    status = status.name,
    assignedTechnicianId = assignedTechnicianId,
    assignedTechnicianName = assignedTechnicianName,
    acknowledgedAt = acknowledgedAt,
    startedAt = startedAt,
    submittedAt = submittedAt,
    finalNotes = finalNotes,
    supervisorApprovedBy = supervisorApprovedBy,
    supervisorApprovedByName = supervisorApprovedByName,
    supervisorApprovedAt = supervisorApprovedAt,
    supervisorRejectedAt = supervisorRejectedAt,
    supervisorRejectionNotes = supervisorRejectionNotes,
    qcAcknowledgedBy = qcAcknowledgedBy,
    qcAcknowledgedByName = qcAcknowledgedByName,
    qcAcknowledgedAt = qcAcknowledgedAt,
    // App stores the checklist as a JSON string; send it as a real JSON object for JSONB.
    qcChecklist = qcChecklistJson?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() },
    closedAt = closedAt,
    overallResult = overallResult,
    taskLogs = taskLogs.map { it.toDto() },
    spares = spares.map { it.toDto() },
    createdAt = generatedAt,
    updatedAt = generatedAt,
)

fun PmWoTaskLog.toDto() = PmTaskLogDto(
    id = id,
    templateItemId = templateItemId,
    orderIndex = orderIndex,
    title = title,
    description = description,
    status = status.name,
    notes = notes,
    completedAt = completedAt,
    completedBy = completedBy,
)

fun PmWoSpare.toDto() = PmSpareDto(
    id = id,
    partId = partId,
    spareName = spareName,
    machineName = machineName,
    quantityUsed = quantityUsed,
    unit = unit,
    notes = notes,
    loggedAt = loggedAt,
    loggedBy = loggedBy,
)
