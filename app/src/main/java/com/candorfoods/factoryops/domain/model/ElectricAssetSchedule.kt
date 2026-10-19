package com.candorfoods.factoryops.domain.model

/**
 * One daily run window, as minute-of-day (IST). 600..780 = 10:00–13:00.
 *
 * A schedule can hold several: a machine that runs a split shift records
 * 10:00–13:00 and 15:00–22:30, and the gap between them draws no power.
 */
data class ScheduleInterval(val startMin: Int, val endMin: Int) {
    val minutes: Int get() = (endMin - startMin).coerceAtLeast(0)
    val hours: Double get() = minutes / 60.0
    val isValid: Boolean get() = startMin in 0 until endMin && endMin <= MINUTES_IN_DAY

    /** True when this and [other] cover any of the same minute. */
    fun overlaps(other: ScheduleInterval): Boolean =
        startMin < other.endMin && other.startMin < endMin

    companion object {
        const val MINUTES_IN_DAY = 24 * 60
    }
}

/**
 * One schedulable row from mt_asset_list ("Electric Asset" or "Production Equipment")
 * plus its daily consumption-recording schedule (GET /asset-schedules). These assets
 * aren't operator-runnable while scheduled — a supervisor sets the recurring windows and
 * the backend records one day's estimated kWh per elapsed day.
 *
 * [intervals] is the schedule: the same set of windows on every day in [days].
 * [startMin]/[endMin] are the backend's derived summary (first start, last end) and are
 * kept only so older readers keep working — read [intervals] for anything real.
 * `estDailyKwh` = ratedKw × total hours × power_factor.
 */
data class ElectricAssetSchedule(
    val assetId: String,
    val assetName: String,
    // Backend category: "Electric Asset" or "Production Equipment" (both schedulable).
    val category: String = "",
    val building: String,
    val subLocation: String,
    val powerLoad: String?,
    val ratedKw: Double?,
    val condition: String?,
    val startMin: Int?,
    val endMin: Int?,
    /** Every window this asset runs in a day, sorted. Empty = no schedule set. */
    val intervals: List<ScheduleInterval> = emptyList(),
    val active: Boolean,
    val hours: Double,
    val estDailyKwh: Double?,
    // Weekdays the windows fire (0=Sunday … 6=Saturday). A day left out is skipped.
    val days: Set<Int> = ALL_DAYS,
    // Run the whole day: the windows are ignored and hours = 24 on each active day.
    val is24h: Boolean = false,
    val updatedBy: String?,
    val updatedAt: String?,
) {
    val hasSchedule: Boolean get() = intervals.isNotEmpty()

    /** More than one window in the day — the card labels these differently. */
    val isSplitShift: Boolean get() = intervals.size > 1

    companion object {
        /** 0=Sunday … 6=Saturday. Default when a schedule has no explicit day set. */
        val ALL_DAYS: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6)
    }
}
