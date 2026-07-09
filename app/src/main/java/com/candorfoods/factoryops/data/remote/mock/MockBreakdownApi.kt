package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.BreakdownApi
import com.candorfoods.factoryops.data.remote.dto.BreakdownFlagResponseDto
import com.candorfoods.factoryops.data.remote.dto.OpenBreakdownDto
import com.candorfoods.factoryops.data.remote.dto.QcAcknowledgeRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcDecisionRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcUpdateResponseDto
import kotlinx.coroutines.delay
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.util.UUID

class MockBreakdownApi : BreakdownApi {
    override suspend fun listOpen(token: String, plantId: String): List<OpenBreakdownDto> {
        delay(200)
        return emptyList()
    }

    override suspend fun raiseFlag(
        token: String,
        machineId: RequestBody,
        operatorId: RequestBody,
        severity: RequestBody,
        description: RequestBody,
        raisedAt: RequestBody,
        beforePhoto: MultipartBody.Part?,
    ): BreakdownFlagResponseDto {
        delay(400)
        return BreakdownFlagResponseDto(
            id = UUID.randomUUID().toString(),
            syncStatus = "SYNCED"
        )
    }

    override suspend fun qcAcknowledge(
        token: String,
        flagId: String,
        request: QcAcknowledgeRequestDto
    ): QcUpdateResponseDto {
        delay(200)
        return QcUpdateResponseDto(
            id = flagId,
            ticketStatus = "PENDING_QC",
            machineStatus = "PENDING_QC",
            qcStatus = "PENDING",
            syncStatus = "SYNCED"
        )
    }

    override suspend fun workDone(
        token: String,
        flagId: String,
        userId: RequestBody,
        userName: RequestBody,
        workDone: RequestBody,
        doneAt: RequestBody,
        afterPhoto: MultipartBody.Part?,
    ): QcUpdateResponseDto {
        delay(200)
        return QcUpdateResponseDto(
            id = flagId,
            ticketStatus = "PENDING_QC",
            machineStatus = "PENDING_QC",
            qcStatus = "PENDING",
            syncStatus = "SYNCED"
        )
    }

    override suspend fun qcApprove(
        token: String,
        flagId: String,
        request: QcDecisionRequestDto
    ): QcUpdateResponseDto {
        delay(200)
        return QcUpdateResponseDto(
            id = flagId,
            ticketStatus = "CLOSED",
            machineStatus = "IDLE",
            qcStatus = "APPROVED",
            syncStatus = "SYNCED"
        )
    }

    override suspend fun qcDisapprove(
        token: String,
        flagId: String,
        request: QcDecisionRequestDto
    ): QcUpdateResponseDto {
        delay(200)
        return QcUpdateResponseDto(
            id = flagId,
            ticketStatus = "REOPENED",
            machineStatus = "PENDING_QC",
            qcStatus = "REJECTED",
            syncStatus = "SYNCED"
        )
    }
}
