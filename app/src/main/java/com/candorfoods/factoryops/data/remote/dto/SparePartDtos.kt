package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.SparePartLogEntry
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── GET /spare-parts ────────────────────────────────────────────────────────

@Serializable
data class SparePartsResponseDto(
    @SerialName("machines") val machines: List<SparePartsMachineDto> = emptyList(),
)

@Serializable
data class SparePartsMachineDto(
    @SerialName("machine_name") val machineName: String = "",
    @SerialName("asset_id")     val assetId: String? = null,
    @SerialName("asset_name")   val assetName: String? = null,
    @SerialName("warehouse")    val warehouse: String? = null,
    @SerialName("parts")        val parts: List<SparePartDto> = emptyList(),
)

@Serializable
data class SparePartDto(
    @SerialName("id")           val id: Int,
    @SerialName("part_name")    val partName: String,
    @SerialName("unit")         val unit: String? = null,
    @SerialName("quantity")     val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int = 0,
    @SerialName("low_stock")    val lowStock: Boolean = false,
)

// ─── GET /spare-parts/picker ─────────────────────────────────────────────────
// Warehouse-scoped, flat list for the work-order spare picker. Separate from the
// grouped /spare-parts response above, which is all-warehouse by design.

@Serializable
data class SparePartPickerResponseDto(
    @SerialName("warehouses") val warehouses: List<String> = emptyList(),
    @SerialName("items")      val items: List<SparePartPickerItemDto> = emptyList(),
)

@Serializable
data class SparePartPickerItemDto(
    @SerialName("part_id")          val partId: Int,
    @SerialName("part_name")        val partName: String,
    @SerialName("unit")             val unit: String? = null,
    @SerialName("quantity")         val quantity: Int,
    @SerialName("min_quantity")     val minQuantity: Int = 0,
    @SerialName("low_stock")        val lowStock: Boolean = false,
    @SerialName("machine_name")     val machineName: String? = null,
    @SerialName("asset_id")         val assetId: String? = null,
    @SerialName("warehouse")        val warehouse: String? = null,
    @SerialName("for_this_machine") val forThisMachine: Boolean = false,
)

// ─── POST /spare-parts and PUT /spare-parts/{id} ─────────────────────────────
// Exactly one of assetId / machineName is sent. warehouse matters only on the
// machineName ("Other") path — with an assetId the server reads it off the asset.

@Serializable
data class SparePartCreateDto(
    @SerialName("asset_id")     val assetId: String? = null,
    @SerialName("machine_name") val machineName: String? = null,
    @SerialName("warehouse")    val warehouse: String? = null,
    @SerialName("part_name")    val partName: String,
    @SerialName("unit")         val unit: String? = null,
    @SerialName("quantity")     val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int = 0,
)

@Serializable
data class SparePartUpdateDto(
    @SerialName("asset_id")     val assetId: String? = null,
    @SerialName("machine_name") val machineName: String? = null,
    @SerialName("warehouse")    val warehouse: String? = null,
    @SerialName("part_name")    val partName: String,
    @SerialName("unit")         val unit: String? = null,
    @SerialName("min_quantity") val minQuantity: Int = 0,
)

// ─── POST /spare-parts/{id}/use and /restock ─────────────────────────────────

@Serializable
data class SparePartActionRequestDto(
    @SerialName("quantity")  val quantity: Int,
    @SerialName("work_type") val workType: String? = null,
    @SerialName("note")      val note: String? = null,
)

// ─── GET /spare-parts/{id}/history ───────────────────────────────────────────

@Serializable
data class SparePartHistoryResponseDto(
    @SerialName("entries") val entries: List<SparePartLogEntryDto> = emptyList(),
)

@Serializable
data class SparePartLogEntryDto(
    @SerialName("id")                val id: Int,
    @SerialName("action")            val action: String,
    @SerialName("direction")         val direction: String,
    @SerialName("quantity")          val quantity: Int,
    @SerialName("note")              val note: String? = null,
    @SerialName("performed_by_name") val performedByName: String? = null,
    @SerialName("performed_at")      val performedAt: String? = null,
)

// ─── mappers ─────────────────────────────────────────────────────────────────

fun SparePartsResponseDto.toDomain(): List<SparePartsMachine> = machines.map { it.toDomain() }

fun SparePartsMachineDto.toDomain() = SparePartsMachine(
    machineName = machineName,
    assetId = assetId,
    assetName = assetName,
    warehouse = warehouse,
    parts = parts.map { it.toDomain() },
)

fun SparePartDto.toDomain() = SparePart(
    id = id,
    partName = partName,
    unit = unit,
    quantity = quantity,
    minQuantity = minQuantity,
    lowStock = lowStock,
)

fun SparePartHistoryResponseDto.toDomain(): List<SparePartLogEntry> = entries.map { it.toDomain() }

fun SparePartLogEntryDto.toDomain() = SparePartLogEntry(
    id = id,
    action = action,
    direction = direction,
    quantity = quantity,
    note = note,
    performedByName = performedByName,
    performedAt = performedAt,
)

fun SparePartPickerResponseDto.toDomain() = SparePartPickerCatalog(
    warehouses = warehouses,
    items = items.map { it.toDomain() },
)

fun SparePartPickerItemDto.toDomain() = SparePartPick(
    partId = partId,
    partName = partName,
    unit = unit,
    quantity = quantity,
    minQuantity = minQuantity,
    lowStock = lowStock,
    machineName = machineName,
    assetId = assetId,
    warehouse = warehouse,
    forThisMachine = forThisMachine,
)
