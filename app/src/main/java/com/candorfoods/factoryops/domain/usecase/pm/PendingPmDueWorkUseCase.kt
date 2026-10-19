package com.candorfoods.factoryops.domain.usecase.pm

import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.util.DateTimeFormatter
import javax.inject.Inject

/**
 * Pure function: the work orders a technician still has to do today.
 *
 * "Still has to do" is the whole point — a submitted or in-QC task has left their hands,
 * and reminding them about it is what buried the tasks that actually needed doing.
 */
class PendingPmDueWorkUseCase @Inject constructor() {

    /**
     * [wos] filtered to work assigned to [techId] that is still pending and due on or
     * before the day of [now], oldest first. Room holds only this technician's slice, but
     * the assignee is checked anyway so a stale cache can't nudge them about someone else's.
     */
    operator fun invoke(wos: List<PmWorkOrder>, techId: String, now: Long): List<PmWorkOrder> {
        val today = DateTimeFormatter.startOfDayMillis(now)
        return wos
            .filter {
                it.assignedTechnicianId == techId &&
                    it.status in PENDING &&
                    DateTimeFormatter.startOfDayMillis(it.scheduledDate) <= today
            }
            .sortedBy { it.scheduledDate }
    }

    private companion object {
        /** Still the technician's to do — anything submitted has moved on to review. */
        val PENDING = setOf(
            PmWoStatus.NOTIFIED, PmWoStatus.ACKNOWLEDGED, PmWoStatus.IN_PROGRESS,
        )
    }
}
