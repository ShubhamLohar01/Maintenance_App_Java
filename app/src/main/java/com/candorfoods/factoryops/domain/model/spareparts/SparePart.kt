package com.candorfoods.factoryops.domain.model.spareparts

/**
 * A readable work type written to mt_spareparts_log.action. [wire] is the
 * exact string the backend validates against — never send the enum name.
 *
 * Deliberately covers only the three OUT types a technician can choose. The
 * server also writes "Restock" and the legacy "Not specified", which arrive as
 * plain text on [SparePartLogEntry.action] and are rendered, not parsed.
 */
enum class WorkType(val wire: String) {
    BREAKDOWN("Breakdown"),
    PM_MAINTENANCE("PM Maintenance"),
    REPLACEMENT("Replacement / Wear & Tear"),
}

/** One spare part row (mt_spareparts) — a part for a machine + quantity on hand. */
data class SparePart(
    val id: Int,
    val partName: String,
    val unit: String?,
    val quantity: Int,
    val minQuantity: Int = 0,
    val lowStock: Boolean = false,
)

/**
 * One machine group from GET /spare-parts. [assetId] is null when the part was
 * added through the "Other" path, or for legacy rows that predate the asset link
 * — those show [machineName] alone.
 */
data class SparePartsMachine(
    val machineName: String,
    val assetId: String?,
    val assetName: String?,
    val warehouse: String?,
    val parts: List<SparePart>,
) {
    /** Groups are keyed on (warehouse, asset or name), matching the server. */
    val key: String get() = "${warehouse.orEmpty()}|${assetId ?: machineName}"

    /** What the UI shows as the machine's title. */
    val displayName: String
        get() = (assetName ?: machineName).ifBlank { "General / Unassigned" }

    val lowStockCount: Int get() = parts.count { it.lowStock }
}

/** One row of a part's usage/restock history. */
data class SparePartLogEntry(
    val id: Int,
    val action: String,          // readable work type, verbatim from the server
    val direction: String,       // IN | OUT
    val quantity: Int,
    val note: String?,
    val performedByName: String?,
    val performedAt: String?,    // ISO 8601
) {
    val isInbound: Boolean get() = direction == "IN"
    val signedQuantity: String get() = if (isInbound) "+$quantity" else "-$quantity"
}
