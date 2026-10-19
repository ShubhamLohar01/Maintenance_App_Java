package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached GET /spare-parts/picker rows, so the work-order spare picker still opens
 * when the technician is out of signal on the shop floor.
 *
 * The catalogue is warehouse-scoped, not machine-scoped: the server's
 * `for_this_machine` flag depends only on which asset was asked about, so it is
 * NOT stored — it is recomputed from [assetId] each time the sheet opens, and one
 * cached catalogue serves every work order in the plant.
 *
 * [quantity] here is a last-synced figure shown for guidance. Stock is only ever
 * really checked when the work order is submitted, so a stale row costs a clear
 * "not enough stock" error there, never a silent over-issue.
 */
@Entity(tableName = "spare_part_picks")
data class SparePartPickEntity(
    @PrimaryKey val partId: Int,
    val partName: String,
    val unit: String?,
    val quantity: Int,
    val minQuantity: Int,
    val lowStock: Boolean,
    val machineName: String?,
    val assetId: String?,
    val warehouse: String?,
    val cachedAt: Long,
)
