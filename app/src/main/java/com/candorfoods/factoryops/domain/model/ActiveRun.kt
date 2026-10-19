package com.candorfoods.factoryops.domain.model

/**
 * A production run that has been started and not yet stopped, as reported by the
 * backend across ALL operators/devices (GET /energy/runs/active). Drives the
 * Head's "currently working" overlay on the machine list. Keyed by [assetId]
 * (asset_id of the mt_asset_list row the operator started production on).
 */
data class ActiveRun(
    val assetId: String,
    val runId: String?,             // server run_id — needed to stop it; null until backend supplies it
    val operatorId: String?,        // who started it — null until backend supplies it
    val operatorName: String?,      // null until the live endpoint supplies it
    val startedAtEpochMs: Long,     // run start, epoch millis (UTC)
    val building: String?,          // "W202" | "A185"; null if backend omits it
)
