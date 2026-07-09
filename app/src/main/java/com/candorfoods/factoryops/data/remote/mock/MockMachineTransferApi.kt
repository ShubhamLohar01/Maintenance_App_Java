package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.MachineTransferApi
import com.candorfoods.factoryops.data.remote.dto.MachineTransferEditDto
import com.candorfoods.factoryops.data.remote.dto.MachineTransferListItemDto
import com.candorfoods.factoryops.data.remote.dto.MachineTransferResponseDto
import kotlinx.coroutines.delay
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

class MockMachineTransferApi : MachineTransferApi {
    override suspend fun create(
        token: String,
        fromWarehouse: RequestBody,
        toWarehouse: RequestBody,
        machineName: RequestBody,
        assetId: RequestBody?,
        date: RequestBody?,
        machineCode: RequestBody?,
        condition: RequestBody?,
        reason: RequestBody?,
        authorisedPerson: RequestBody?,
        remarks: RequestBody?,
        proofPhoto: MultipartBody.Part?,
    ): MachineTransferResponseDto {
        delay(400)
        return MachineTransferResponseDto(
            id = 1,
            proofPhotoUrl = if (proofPhoto != null) "https://mock.local/machine-transfers/1.jpg" else null,
        )
    }

    override suspend fun list(token: String): List<MachineTransferListItemDto> {
        delay(200)
        return emptyList()
    }

    override suspend fun acknowledge(token: String, id: Int): MachineTransferListItemDto {
        delay(200)
        return MachineTransferListItemDto(id = id, status = "APPROVED", canAcknowledge = false)
    }

    override suspend fun update(token: String, id: Int, body: MachineTransferEditDto): MachineTransferListItemDto {
        delay(200)
        return MachineTransferListItemDto(id = id, status = "PENDING", canEdit = true)
    }

    override suspend fun delete(token: String, id: Int): Response<Unit> {
        delay(200)
        return Response.success(Unit)
    }
}
