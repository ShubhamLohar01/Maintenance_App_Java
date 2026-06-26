package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.deriveMachineSection
import com.candorfoods.factoryops.util.PowerLoad
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `mt_asset_list` row served by GET /mt-machines. The Json reader is
 * configured with ignoreUnknownKeys, so we declare only what the UI uses.
 */
@Serializable
data class MtMachineDto(
    @SerialName("asset_id")     val assetId: String,
    @SerialName("building")     val building: String = "",
    @SerialName("asset_name")   val assetName: String = "",
    @SerialName("category")     val category: String = "",
    @SerialName("sub_location") val subLocation: String = "",
    @SerialName("power_load")   val powerLoad: String? = null,
    @SerialName("rated_kw")     val ratedKw: Double? = null,
    @SerialName("quantity")     val quantity: Int? = null,
    @SerialName("condition")    val condition: String? = null,
    @SerialName("assigned_to")  val assignedTo: String? = null,
)

fun MtMachineDto.toDomain(): MtMachine = MtMachine(
    assetId      = assetId,
    building     = building,
    assetName    = assetName,
    category     = category,
    subLocation  = subLocation,
    ratedKw      = ratedKw ?: PowerLoad.parseKw(powerLoad),   // prefer backend, fall back to client parse
    powerLoadRaw = powerLoad?.takeIf { it.isNotBlank() },
    section      = deriveMachineSection(category),
    quantity     = quantity,
    condition    = condition,
    assignedTo   = assignedTo,
)
