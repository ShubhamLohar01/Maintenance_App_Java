package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.ScheduleInterval
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One daily run window on a schedule. Minute-of-day, IST. */
@Serializable
data class AssetScheduleIntervalDto(
    @SerialName("start_min")   val startMin: Int,
    @SerialName("end_min")     val endMin: Int,
    @SerialName("start_label") val startLabel: String? = null,
    @SerialName("end_label")   val endLabel: String? = null,
)

/** One row from GET /asset-schedules (mt_asset_list 'Electric Asset' + its schedule). */
@Serializable
data class AssetScheduleDto(
    @SerialName("asset_id")      val assetId: String,
    @SerialName("asset_name")    val assetName: String = "",
    @SerialName("category")      val category: String = "",
    @SerialName("building")      val building: String = "",
    @SerialName("sub_location")  val subLocation: String = "",
    @SerialName("power_load")    val powerLoad: String? = null,
    @SerialName("rated_kw")      val ratedKw: Double? = null,
    @SerialName("condition")     val condition: String? = null,
    @SerialName("start_min")     val startMin: Int? = null,
    @SerialName("end_min")       val endMin: Int? = null,
    // Every window in the day. Absent on a backend that predates split shifts — the
    // start_min/end_min pair above is then read as the single window.
    @SerialName("intervals")     val intervals: List<AssetScheduleIntervalDto>? = null,
    @SerialName("active")        val active: Boolean = false,
    @SerialName("hours")         val hours: Double = 0.0,
    @SerialName("est_daily_kwh") val estDailyKwh: Double? = null,
    // Weekdays the window fires, e.g. ["MON","TUE",...]. null/absent = every day.
    @SerialName("days")          val days: List<String>? = null,
    // Whole-day run: start/end window ignored, hours = 24 on each active day.
    @SerialName("is_24h")        val is24h: Boolean = false,
    @SerialName("updated_by")    val updatedBy: String? = null,
    @SerialName("updated_at")    val updatedAt: String? = null,
)

/**
 * Body for PUT /asset-schedules/{machineId} — set/replace the daily windows.
 *
 * [intervals] is the real payload. [startMin]/[endMin] are still sent as the derived
 * first-start / last-end summary so a backend that predates split shifts keeps working
 * (it reads them as the one window and ignores the list).
 */
@Serializable
data class AssetScheduleUpsertDto(
    @SerialName("start_min") val startMin: Int,
    @SerialName("end_min")   val endMin: Int,
    @SerialName("intervals") val intervals: List<AssetScheduleIntervalDto>,
    @SerialName("active")    val active: Boolean = true,
    // Weekdays the windows fire, e.g. ["MON","TUE",...]. Empty = never fires.
    @SerialName("days")      val days: List<String>,
    // Whole-day run: backend ignores the windows and treats each active day as 24h.
    @SerialName("is_24h")    val is24h: Boolean = false,
)

/** Day codes ordered Sunday-first, so index = the 0=Sunday … 6=Saturday convention
 *  used by [ElectricAssetSchedule.days]. Shared by both the DTO mapping and upsert. */
val SCHEDULE_DAY_CODES: List<String> = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

/** ["MON","WED"] -> {1, 3}. Unknown codes are ignored; null/empty -> every day so a
 *  legacy row without the field keeps its old every-day behaviour. */
fun codesToDays(codes: List<String>?): Set<Int> {
    if (codes.isNullOrEmpty()) return ElectricAssetSchedule.ALL_DAYS
    return codes.mapNotNull { c -> SCHEDULE_DAY_CODES.indexOf(c.trim().uppercase()).takeIf { it >= 0 } }.toSet()
        .ifEmpty { ElectricAssetSchedule.ALL_DAYS }
}

/** {1, 3} -> ["MON","WED"], ordered Sunday-first. */
fun daysToCodes(days: Set<Int>): List<String> =
    SCHEDULE_DAY_CODES.filterIndexed { index, _ -> index in days }

/**
 * The windows this schedule runs, sorted and sanity-checked.
 *
 * Falls back to the [AssetScheduleDto.startMin]/[AssetScheduleDto.endMin] pair when the
 * backend hasn't got the `intervals` field yet, so the app works against either version.
 */
fun AssetScheduleDto.toIntervals(): List<ScheduleInterval> {
    val listed = intervals
        ?.map { ScheduleInterval(it.startMin, it.endMin) }
        ?.filter { it.isValid }
        ?.sortedBy { it.startMin }
        .orEmpty()
    if (listed.isNotEmpty()) return listed
    val legacy = ScheduleInterval(startMin ?: return emptyList(), endMin ?: return emptyList())
    return if (legacy.isValid) listOf(legacy) else emptyList()
}

fun AssetScheduleDto.toDomain(): ElectricAssetSchedule = ElectricAssetSchedule(
    assetId = assetId,
    assetName = assetName,
    category = category,
    building = building,
    subLocation = subLocation,
    powerLoad = powerLoad?.takeIf { it.isNotBlank() },
    ratedKw = ratedKw,
    condition = condition,
    startMin = startMin,
    endMin = endMin,
    intervals = toIntervals(),
    active = active,
    hours = hours,
    estDailyKwh = estDailyKwh,
    days = codesToDays(days),
    is24h = is24h,
    updatedBy = updatedBy,
    updatedAt = updatedAt,
)
