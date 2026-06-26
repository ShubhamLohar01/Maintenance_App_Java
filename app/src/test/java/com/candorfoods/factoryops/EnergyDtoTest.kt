package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.DailyHistoryDto
import com.candorfoods.factoryops.data.remote.dto.RunStartRequestDto
import com.candorfoods.factoryops.data.remote.dto.RunStartResponseDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `start request serialises client_run_id`() {
        val s = json.encodeToString(
            RunStartRequestDto(
                machineId = "W202-0005", clientRunId = "uuid-1",
                startedAt = 1750000000000, scheduledEndAt = 1750003600000,
            )
        )
        assertTrue(s.contains("\"client_run_id\":\"uuid-1\""), s)
        assertTrue(s.contains("\"machine_id\":\"W202-0005\""), s)
    }

    @Test fun `start response parses without operator_id`() {
        val r = json.decodeFromString<RunStartResponseDto>(
            """{"run_id":"r1","client_run_id":"uuid-1","started_at":1,"scheduled_end_at":2}"""
        )
        assertEquals("r1", r.runId)
        assertEquals("uuid-1", r.clientRunId)
    }

    @Test fun `history parses as a list of daily summaries`() {
        val list = json.decodeFromString<List<DailyHistoryDto>>(
            """[{"date":"2026-06-20","total_run_hours":1.0,"total_kwh":0.12,
                 "estimated_cost":1.02,"runs":[{"id":"run-1","started_at":1,"ended_at":2,
                 "duration_hours":1.0,"kwh":0.12}]}]"""
        )
        assertEquals(1, list.size)
        assertEquals("2026-06-20", list[0].date)
        assertEquals(1, list[0].runs.size)
        assertEquals("run-1", list[0].runs[0].id)
    }
}
