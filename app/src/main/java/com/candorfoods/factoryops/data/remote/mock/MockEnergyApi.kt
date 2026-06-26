package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.EnergyApi
import com.candorfoods.factoryops.data.remote.dto.*
import kotlinx.coroutines.delay
import java.util.UUID

class MockEnergyApi : EnergyApi {
    override suspend fun startRun(token: String, request: RunStartRequestDto): RunStartResponseDto {
        delay(300)
        return RunStartResponseDto(
            runId = UUID.randomUUID().toString(),
            clientRunId = request.clientRunId,
            startedAt = request.startedAt,
            scheduledEndAt = request.scheduledEndAt,
        )
    }

    override suspend fun stopRun(token: String, runId: String, request: RunStopRequestDto): RunStopResponseDto {
        delay(300)
        return RunStopResponseDto(runId = runId, endedAt = request.endedAt, computedKwh = 87.5)
    }

    override suspend fun getMachineHistory(
        token: String, machineId: String, from: Long, to: Long
    ): List<DailyHistoryDto> {
        delay(500)
        return emptyList()
    }

    override suspend fun getActiveRuns(token: String): List<ActiveRunDto> {
        delay(300)
        return emptyList()
    }
}
