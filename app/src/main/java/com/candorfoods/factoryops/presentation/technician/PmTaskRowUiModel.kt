package com.candorfoods.factoryops.presentation.technician

import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.PmWorkOrder

data class PmTaskRowUiModel(
    val wo: PmWorkOrder,
    val machineName: String,
    val machineCode: String,
    val machineLocation: String,
    val criticality: Criticality
) {
    /** Negative = overdue, 0 = due today, positive = future. */
    val daysToDue: Int
        get() {
            val dayMs = 86_400_000L
            val now = System.currentTimeMillis()
            val today = (now / dayMs) * dayMs
            val due = (wo.scheduledDate / dayMs) * dayMs
            return ((due - today) / dayMs).toInt()
        }

    val isOverdue: Boolean get() = daysToDue < 0
    val isCritical: Boolean get() = criticality == Criticality.A
}
