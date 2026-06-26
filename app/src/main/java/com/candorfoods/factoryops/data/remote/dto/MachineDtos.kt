package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MachineDto(
    val id: String,
    val code: String,
    val name: String,
    val location: String,
    // Building this machine lives in ("W-202" | "A-185"). Defaults to "W-202"
    // for backward-compat: older /machines payloads omit it, and that matches
    // the mt_floor_utility_readings.building column default on the backend.
    @SerialName("building") val building: String = "W-202",
    @SerialName("plant_id") val plantId: String,
    @SerialName("machine_type") val machineType: String = "OTHER",
    // Free-text classification from the backend (e.g. "Packaging", "Sealing").
    // Nullable + open-ended — render as-is, never map to an enum.
    @SerialName("category") val category: String? = null,
    @SerialName("rated_kw") val ratedKw: Double,
    // load_factor is no longer part of the kWh calc (backend uses a fixed power
    // factor now). Defaulted so the app doesn't crash deserializing /machines
    // payloads that have dropped these columns.
    @SerialName("load_factor") val loadFactor: Double = 0.0,
    @SerialName("load_factor_source") val loadFactorSource: String = "ASSUMED",
    val criticality: String,
    @SerialName("expected_run_hours") val expectedRunHours: Double,
    @SerialName("current_status") val currentStatus: String,
    @SerialName("updated_at") val updatedAt: Long
)
