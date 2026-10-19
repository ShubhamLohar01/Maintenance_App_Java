package com.candorfoods.factoryops.domain.usecase.pm

import com.candorfoods.factoryops.util.Constants
import com.candorfoods.factoryops.util.DateTimeFormatter

/**
 * When the technician's single daily "PM pending" notice may fire.
 *
 * The PM worker runs hourly because it also syncs, so the notice needs its own rules or
 * it repeats all day. Two of them: at most one per calendar day (IST), and not before
 * shift start — the previous behaviour let the first run after midnight buzz at 2am.
 */
object PmDueNoticePolicy {

    /**
     * True when [pendingCount] work is outstanding, the IST clock has reached shift start,
     * and no notice has gone out yet on the IST day containing [now].
     *
     * [lastNoticeDate] is the ISO date of the last notice, or null if there has never
     * been one.
     */
    fun shouldNotify(pendingCount: Int, lastNoticeDate: String?, now: Long): Boolean =
        pendingCount > 0 &&
            DateTimeFormatter.hourOfDayIst(now) >= Constants.PM_DUE_NOTICE_HOUR_IST &&
            lastNoticeDate != DateTimeFormatter.isoDate(now)
}
