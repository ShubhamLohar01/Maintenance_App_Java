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
    @SerialName("model_no")     val modelNo: String? = null,
    @SerialName("serial_no")    val serialNo: String? = null,
    @SerialName("remarks")      val remarks: String? = null,
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
    modelNo      = modelNo,
    serialNo     = serialNo,
    remarks      = remarks,
)

/**
 * Body for PUT /mt-machines/{asset_id} — the supervisor-editable columns of
 * `mt_asset_list`. `asset_id` is the path param (immutable). `rated_kw` is NOT sent;
 * the backend recomputes it from `power_load`.
 */
@Serializable
data class MtMachineUpdateDto(
    @SerialName("building")     val building: String,
    @SerialName("asset_name")   val assetName: String,
    @SerialName("category")     val category: String,
    @SerialName("sub_location") val subLocation: String,
    @SerialName("quantity")     val quantity: Int? = null,
    @SerialName("model_no")     val modelNo: String? = null,
    @SerialName("serial_no")    val serialNo: String? = null,
    @SerialName("power_load")   val powerLoad: String? = null,
    @SerialName("condition")    val condition: String? = null,
    @SerialName("remarks")      val remarks: String? = null,
)

fun MtMachine.toUpdateDto(): MtMachineUpdateDto = MtMachineUpdateDto(
    building    = building,
    assetName   = assetName,
    category    = category,
    subLocation = subLocation,
    quantity    = quantity,
    modelNo     = modelNo,
    serialNo    = serialNo,
    powerLoad   = powerLoadRaw,
    condition   = condition,
    remarks     = remarks,
)

/**
 * Body for POST /mt-machines — a full `mt_asset_list` row for a newly added asset.
 * `asset_id` is sent blank (backend assigns it) and `rated_kw` is omitted (backend
 * computes it from `power_load`).
 */
fun MtMachine.toAddDto(): MtMachineDto = MtMachineDto(
    assetId     = "",
    building    = building,
    assetName   = assetName,
    category    = category,
    subLocation = subLocation,
    powerLoad   = powerLoadRaw,
    ratedKw     = null,
    quantity    = quantity,
    condition   = condition,
    assignedTo  = assignedTo,
    modelNo     = modelNo,
    serialNo    = serialNo,
    remarks     = remarks,
)
