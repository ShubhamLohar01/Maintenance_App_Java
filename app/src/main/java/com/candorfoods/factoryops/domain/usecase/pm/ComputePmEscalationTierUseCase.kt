package com.candorfoods.factoryops.domain.usecase.pm

import com.candorfoods.factoryops.util.Constants
import javax.inject.Inject

/**
 * Pure function: given the WO's scheduled date and the current time, return the
 * escalation tier (0..4) the WO should have. Tier 0 means on-time (or early).
 */
class ComputePmEscalationTierUseCase @Inject constructor() {

    operator fun invoke(scheduledDate: Long, now: Long): Int {
        val daysOverdue = ((now - scheduledDate).coerceAtLeast(0L) / DAY_MS)
        return when {
            daysOverdue >= Constants.PM_TIER_4_DAYS -> 4
            daysOverdue >= Constants.PM_TIER_3_DAYS -> 3
            daysOverdue >= Constants.PM_TIER_2_DAYS -> 2
            daysOverdue >= Constants.PM_TIER_1_DAYS -> 1
            else                                     -> 0
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
