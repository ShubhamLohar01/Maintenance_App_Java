package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MachineTransfer
import com.candorfoods.factoryops.domain.model.NewMachineTransfer
import com.candorfoods.factoryops.domain.model.Result

interface MachineTransferRepository {
    /** POST /machine-transfers (multipart). Streams the optional proof photo as a file part. */
    suspend fun createTransfer(input: NewMachineTransfer): Result<MachineTransfer>

    /** GET /machine-transfers. */
    suspend fun listTransfers(): Result<List<MachineTransfer>>
}
