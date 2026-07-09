package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row from GET /asset-schedules (mt_asset_list 'Electric Asset' + its schedule). */
@Serializable
data class AssetScheduleDto(
    @SerialName("asset_id")      val assetId: String,
    @SerialName("asset_name")    val assetName: String = "",
    @SerialName("building")      val building: String = "",
    @SerialName("sub_location")  val subLocation: String = "",
    @SerialName("power_load")    val powerLoad: String? = null,
    @SerialName("rated_kw")      val ratedKw: Double? = null,
    @SerialName("condition")     val condition: String? = null,
    @SerialName("start_min")     val startMin: Int? = null,
    @SerialName("end_min")       val endMin: Int? = null,
    @SerialName("active")        val active: Boolean = false,
    @SerialName("hours")         val hours: Double = 0.0,
    @SerialName("est_daily_kwh") val estDailyKwh: Double? = null,
    @SerialName("updated_by")    val updatedBy: String? = null,
    @SerialName("updated_at")    val updatedAt: String? = null,
)

/** Body for PUT /asset-schedules/{machineId} — set/replace the daily window. */
@Serializable
data class AssetScheduleUpsertDto(
    @SerialName("start_min") val startMin: Int,
    @SerialName("end_min")   val endMin: Int,
    @SerialName("active")    val active: Boolean = true,
)

fun AssetScheduleDto.toDomain(): ElectricAssetSchedule = ElectricAssetSchedule(
    assetId = assetId,
    assetName = assetName,
    building = building,
    subLocation = subLocation,
    powerLoad = powerLoad?.takeIf { it.isNotBlank() },
    ratedKw = ratedKw,
    condition = condition,
    startMin = startMin,
    endMin = endMin,
    active = active,
    hours = hours,
    estDailyKwh = estDailyKwh,
    updatedBy = updatedBy,
    updatedAt = updatedAt,
)
