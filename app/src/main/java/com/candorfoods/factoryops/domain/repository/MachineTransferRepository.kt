package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MachineTransfer
import com.candorfoods.factoryops.domain.model.NewMachineTransfer
import com.candorfoods.factoryops.domain.model.Result

interface MachineTransferRepository {
    /** POST /machine-transfers (multipart). Streams the optional proof photo as a file part. */
    suspend fun createTransfer(input: NewMachineTransfer): Result<MachineTransfer>

    /** GET /machine-transfers. */
    suspend fun listTransfers(): Result<List<MachineTransfer>>

    /** POST /machine-transfers/{id}/acknowledge — receiving warehouse confirms receipt
     *  (status PENDING -> APPROVED). Backend enforces who may acknowledge. */
    suspend fun acknowledgeTransfer(id: Int): Result<MachineTransfer>

    /** PUT /machine-transfers/{id} — edit a still-PENDING transfer (creator only). */
    suspend fun updateTransfer(id: Int, input: NewMachineTransfer): Result<MachineTransfer>

    /** DELETE /machine-transfers/{id} — creator deletes their still-PENDING transfer. */
    suspend fun deleteTransfer(id: Int): Result<Unit>
}
