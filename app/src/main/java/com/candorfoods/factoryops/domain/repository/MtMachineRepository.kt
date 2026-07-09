package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result

interface MtMachineRepository {
    /** Asset register. Filters are optional; null means "all". */
    suspend fun list(
        building: String? = null,
        subLocation: String? = null,
        subCategory: String? = null,
    ): Result<List<MtMachine>>

    /** Fetch a single asset by its asset_id (client-side filter of the register). */
    suspend fun getByAssetId(assetId: String): Result<MtMachine?>

    /** Admin-only: append a new asset row (lands in OTHERS unless it's Production Equipment). */
    suspend fun addOther(
        name: String,
        building: String,
        subLocation: String,
        subCategory: String,
        powerLoad: String?,
    ): Result<MtMachine>

    /** Append a new asset row from a fully-populated MtMachine → POST /mt-machines. */
    suspend fun addAsset(machine: MtMachine): Result<MtMachine>

    /** Supervisor: update an existing asset's editable fields → PUT /mt-machines/{asset_id}. */
    suspend fun updateAsset(machine: MtMachine): Result<MtMachine>
}
