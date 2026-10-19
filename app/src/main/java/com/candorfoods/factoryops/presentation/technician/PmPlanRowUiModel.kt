package com.candorfoods.factoryops.presentation.technician

import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.util.Constants

/**
 * A single assigned PM plan (from mt_pm_plan) shown on the technician's PM screen. This is
 * the recurring schedule/assignment — it exists immediately when a supervisor assigns it,
 * unlike a work order which only appears once the plan comes due. Carries the checklist
 * snapshot so the plan-detail sheet can preview the upcoming task.
 */
data class PmPlanRowUiModel(
    val planId: String,
    val machineName: String,
    val machineCode: String,
    val nextDueAt: Long,
    val triggerType: PmTriggerType,
    val triggerInterval: Int,
    val description: String = "",
    val items: List<PmTemplateItem> = emptyList(),
    /** The plan's most recent work order, whatever its status — what the technician
     *  executes. Null only until the backend's generation sweep creates the first one. */
    val woId: String? = null,
    val woStatus: PmWoStatus? = null,
) {
    /** True when the current cycle is still the technician's to do. */
    val isStartable: Boolean
        get() = woStatus == PmWoStatus.NOTIFIED ||
                woStatus == PmWoStatus.ACKNOWLEDGED ||
                woStatus == PmWoStatus.IN_PROGRESS

    /** Where this plan's current cycle stands, in the technician's terms. */
    val actionHint: String
        get() = when (woStatus) {
            null                           -> "Not generated yet"
            PmWoStatus.NOTIFIED            -> "Ready to start →"
            PmWoStatus.ACKNOWLEDGED        -> "Acknowledged →"
            PmWoStatus.IN_PROGRESS         -> "In progress →"
            PmWoStatus.SUBMITTED,
            PmWoStatus.SUPERVISOR_APPROVED,
            PmWoStatus.PENDING_QC          -> "Submitted — in review"
            else                           -> "Completed"
        }

    /** Negative = overdue, 0 = due today, positive = future. */
    val daysToDue: Int
        get() {
            val dayMs = 86_400_000L
            val today = (System.currentTimeMillis() / dayMs) * dayMs
            val due = (nextDueAt / dayMs) * dayMs
            return ((due - today) / dayMs).toInt()
        }

    val intervalLabel: String
        get() = when (triggerType) {
            PmTriggerType.TIME  -> "Every $triggerInterval days"
            PmTriggerType.USAGE -> "Every $triggerInterval running hours"
        }

    /** When the backend generator will turn this plan into a work order (lead window). */
    val generatesAt: Long
        get() = nextDueAt - Constants.PM_GENERATION_LEAD_DAYS * 86_400_000L

    /** True once the plan is inside the lead window — its task should already exist. */
    val isInsideGenerationWindow: Boolean
        get() = System.currentTimeMillis() >= generatesAt
}
