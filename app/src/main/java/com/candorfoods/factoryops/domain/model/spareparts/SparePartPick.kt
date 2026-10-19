package com.candorfoods.factoryops.domain.model.spareparts

/**
 * A work order may carry at most this many spare lines. Mirrors
 * schemas.MAX_WO_SPARES — the server rejects an eleventh, so the UI stops at ten
 * rather than letting a technician fill in a row that will bounce.
 */
const val MAX_WO_SPARES = 10

/**
 * One selectable row from GET /spare-parts/picker — the stock a technician may
 * consume while closing a PM or a breakdown.
 *
 * Distinct from [SparePart] (the stock-management screen's row) because the picker
 * is a FLAT, warehouse-scoped list rather than a machine-grouped one: the server
 * only returns parts from the caller's own plant, and marks the ones fitted to the
 * machine being worked on so they can be shown first.
 *
 * [quantity] is what was on hand at the last sync. It is advisory — the server
 * re-checks stock when the work order is submitted, so a stale cache surfaces as a
 * "not enough stock" error there rather than as a silent over-issue.
 */
data class SparePartPick(
    val partId: Int,
    val partName: String,
    val unit: String?,
    val quantity: Int,
    val minQuantity: Int = 0,
    val lowStock: Boolean = false,
    val machineName: String?,
    val assetId: String?,
    val warehouse: String?,
    val forThisMachine: Boolean = false,
) {
    val inStock: Boolean get() = quantity > 0

    /** Matches a search box against both the part and the machine it belongs to —
     *  a technician looking for "mixer" wants the mixer's parts. */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return partName.contains(q, ignoreCase = true) ||
            machineName.orEmpty().contains(q, ignoreCase = true)
    }
}

/**
 * The picker's whole catalogue. [warehouses] is what the caller is scoped to — used
 * to label the "other parts" group, and still meaningful when [items] is empty
 * (an all-plant role, or a technician whose plant simply has no stock rows yet).
 */
data class SparePartPickerCatalog(
    val warehouses: List<String> = emptyList(),
    val items: List<SparePartPick> = emptyList(),
    /**
     * Why the catalogue could not be refreshed, if it could not. An empty list is
     * ambiguous on its own — no stock registered, an expired session, a backend
     * error and a dead tunnel all produce one — and telling a technician "no parts
     * are registered" when the truth is "the server said 401" sends them to the
     * wrong person. When this is set, [items] is whatever the cache still held.
     */
    val error: String? = null,
) {
    val label: String get() = warehouses.joinToString(" / ").ifBlank { "your warehouse" }
}
