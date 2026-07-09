package com.candorfoods.factoryops.domain.model

/**
 * One "Electric Asset" row from mt_asset_list plus its daily consumption-recording
 * schedule (GET /asset-schedules). These assets aren't operator-runnable, so a
 * supervisor sets a recurring daily window; the backend then records one day's
 * estimated kWh per elapsed window. `startMin`/`endMin` are minute-of-day (IST);
 * both null = no schedule set. `estDailyKwh` = ratedKw × hours × power_factor.
 */
data class ElectricAssetSchedule(
    val assetId: String,
    val assetName: String,
    val building: String,
    val subLocation: String,
    val powerLoad: String?,
    val ratedKw: Double?,
    val condition: String?,
    val startMin: Int?,
    val endMin: Int?,
    val active: Boolean,
    val hours: Double,
    val estDailyKwh: Double?,
    val updatedBy: String?,
    val updatedAt: String?,
) {
    val hasSchedule: Boolean get() = startMin != null && endMin != null
}
