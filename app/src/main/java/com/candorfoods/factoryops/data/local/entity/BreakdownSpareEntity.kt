package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A spare the technician picked while repairing a breakdown, held locally until
 * "Submit resolution" posts the whole list to POST /breakdowns/{id}/work-done.
 *
 * The breakdown mirror of [PmWoSpareEntity] — same fields, same picker, same
 * server-side deduction — but a separate table because a breakdown is keyed by
 * flag id, not work-order id.
 *
 * There is no syncStatus: unlike a PM, nothing here is enqueued for background
 * sync. The resolve call is backend-authoritative (it must move stock and upload
 * the after-photo), so these rows exist only between picking and submitting.
 */
@Entity(tableName = "breakdown_spares")
data class BreakdownSpareEntity(
    @PrimaryKey val id: String,
    val flagId: String,
    val partId: Int,
    val spareName: String,
    val machineName: String?,
    val quantityUsed: Double,
    val unit: String,
    val notes: String?,
    val loggedAt: Long,
)
