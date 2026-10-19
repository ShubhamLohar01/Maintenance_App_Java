package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.TradeType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire model for the backend `mt_pm_plan` table (see pm-backend-integration-spec).
 * ONE backend plan == the app's [PmPlan] (schedule) + [PmTemplate] (name / description
 * / checklist) merged. The mappers below split it on read and re-join it on write.
 */
@Serializable
data class PmPlanDto(
    /** Server-assigned on create ('PLANAA001'…; legacy rows keep 'plan-<uuid>'). Any id
     *  sent in a POST body is ignored — adopt the id from the response. */
    val id: String,
    /** The app's locally-generated uuid, sent on create as the idempotency key — a
     *  lost-response retry with the same client_ref updates instead of duplicating.
     *  Echoed back in responses so the app can reconcile its provisional row. */
    @SerialName("client_ref")             val clientRef: String? = null,
    @SerialName("machine_id")             val machineId: String,
    @SerialName("machine_name")           val machineName: String,
    val description: String = "",
    val items: List<PmPlanItemDto> = emptyList(),
    @SerialName("trigger_type")           val triggerType: String = "TIME",
    @SerialName("trigger_interval")       val triggerInterval: Int,
    @SerialName("next_due_at")            val nextDueAt: Long,
    @SerialName("last_completed_at")      val lastCompletedAt: Long? = null,
    @SerialName("assigned_technician_id") val assignedTechnicianId: String,
    @SerialName("is_active")              val isActive: Boolean = true,
    @SerialName("created_by")             val createdBy: String? = null,
    @SerialName("created_at")             val createdAt: Long,
    @SerialName("updated_at")             val updatedAt: Long,
)

@Serializable
data class PmPlanItemDto(
    val id: String,
    @SerialName("order_index")          val orderIndex: Int = 0,
    val title: String = "",
    val description: String = "",   // "Remarks" (supervisor guidance; was "instruction")
)

// ─── Read: backend plan → the app's (PmPlan, PmTemplate) pair ────────────────

/** The app keeps a plan as two rows; derive the template id deterministically. */
private fun templateIdFor(planId: String) = "tmpl-$planId"

fun PmPlanDto.toDomain(): Pair<PmPlan, PmTemplate> {
    val templateId = templateIdFor(id)
    val template = PmTemplate(
        id = templateId,
        name = machineName,
        machineType = MachineType.OTHER,          // dropped on backend → default
        description = description,
        estimatedDurationMinutes = 60,            // dropped on backend → app default
        defaultTradeType = TradeType.MECHANICAL,  // dropped on backend → default
        createdBy = createdBy.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        items = items.map { it.toDomain(templateId) },
    )
    val plan = PmPlan(
        id = id,
        machineId = machineId,
        templateId = templateId,
        triggerType = runCatching { PmTriggerType.valueOf(triggerType) }.getOrDefault(PmTriggerType.TIME),
        triggerInterval = triggerInterval,
        lastCompletedAt = lastCompletedAt,
        lastCompletedAtHours = null,              // app-only, not tracked server-side
        nextDueAt = nextDueAt,
        assignedTechnicianId = assignedTechnicianId,
        isActive = isActive,
        createdAt = createdAt,
    )
    return plan to template
}

fun PmPlanItemDto.toDomain(templateId: String) = PmTemplateItem(
    id = id,
    templateId = templateId,
    orderIndex = orderIndex,
    title = title,
    description = description,
)

// ─── Write: the app's plan + template → one backend plan payload ─────────────

fun pmPlanDto(plan: PmPlan, template: PmTemplate, clientRef: String? = null): PmPlanDto = PmPlanDto(
    id = plan.id,
    clientRef = clientRef,
    machineId = plan.machineId,
    machineName = template.name,
    description = template.description,
    items = template.items.map { it.toDto() },
    triggerType = plan.triggerType.name,
    triggerInterval = plan.triggerInterval,
    nextDueAt = plan.nextDueAt,
    lastCompletedAt = plan.lastCompletedAt,
    assignedTechnicianId = plan.assignedTechnicianId,
    isActive = plan.isActive,
    createdBy = template.createdBy.ifBlank { null },
    createdAt = plan.createdAt,
    updatedAt = template.updatedAt,
)

fun PmTemplateItem.toDto() = PmPlanItemDto(
    id = id,
    orderIndex = orderIndex,
    title = title,
    description = description,
)
