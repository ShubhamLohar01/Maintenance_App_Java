package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.SparePartLogEntry
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import com.candorfoods.factoryops.domain.model.spareparts.WorkType

/** Spare Parts stock, backed by the backend /spare-parts API. */
interface SparePartsRepository {

    /** Every machine's parts + quantity on hand, across all warehouses. */
    suspend fun list(): Result<List<SparePartsMachine>>

    /**
     * Stock the caller may consume on a work order — their own warehouse only,
     * with [assetId]'s parts flagged so the picker can list them first.
     *
     * Never fails: the response is cached, and a network error falls back to the
     * last synced catalogue so a technician on the shop floor can still close a
     * job. Cached quantities are advisory — the server re-checks stock at submit.
     */
    suspend fun picker(assetId: String?): SparePartPickerCatalog

    /**
     * Add a part. Pass exactly one of [assetId] or [machineName]; [warehouse] is
     * needed only alongside [machineName] (with an asset the server reads it off
     * the register). Fails with a 409 message if the machine already has a part
     * by that name.
     */
    suspend fun create(
        assetId: String?,
        machineName: String?,
        warehouse: String?,
        partName: String,
        unit: String?,
        quantity: Int,
        minQuantity: Int,
    ): Result<SparePart>

    /** Edit a part's details. Quantity is not editable — use [use] / [restock]. */
    suspend fun update(
        partId: Int,
        assetId: String?,
        machineName: String?,
        warehouse: String?,
        partName: String,
        unit: String?,
        minQuantity: Int,
    ): Result<SparePart>

    /** Soft-delete a part. Fails for any role but SUPERVISOR/ADMIN. */
    suspend fun delete(partId: Int): Result<Unit>

    /** Log usage against a work type and decrement. Fails if it exceeds stock. */
    suspend fun use(partId: Int, quantity: Int, workType: WorkType, note: String?): Result<SparePart>

    /** Log a restock and increment quantity. */
    suspend fun restock(partId: Int, quantity: Int, note: String?): Result<SparePart>

    /** One part's usage/restock log, newest first. */
    suspend fun history(partId: Int): Result<List<SparePartLogEntry>>
}
