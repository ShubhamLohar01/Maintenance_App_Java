package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.MachineDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the `building` plumbing added for mt_floor_utility_readings:
 *   - a /machines payload that omits `building` still parses (defaults to W-202),
 *     so the app keeps working before the Python backend ships the column;
 *   - a payload that includes it is honoured (A-185 isn't lost).
 */
class UtilityReadingBuildingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun machineJson(buildingField: String) = """
        {
          "id": "mach-005", "code": "WH1-CONV-01", "name": "Conveyor Belt 1",
          "location": "Warehouse 1", $buildingField "plant_id": "PLANT-01",
          "rated_kw": 3.0, "load_factor": 0.5, "load_factor_source": "ASSUMED",
          "criticality": "C", "expected_run_hours": 8.0,
          "current_status": "IDLE", "updated_at": 123
        }
    """.trimIndent()

    @Test
    fun `machine payload without building defaults to W-202`() {
        val dto = json.decodeFromString<MachineDto>(machineJson(buildingField = ""))
        assertEquals("W-202", dto.building)
    }

    @Test
    fun `machine payload with building is honoured`() {
        val dto = json.decodeFromString<MachineDto>(machineJson(buildingField = "\"building\": \"A-185\","))
        assertEquals("A-185", dto.building)
    }

    @Test
    fun `machine payload parses category and defaults null when absent`() {
        val withCategory = json.decodeFromString<MachineDto>(
            machineJson(buildingField = "\"category\": \"Packaging\",")
        )
        assertEquals("Packaging", withCategory.category)

        val withoutCategory = json.decodeFromString<MachineDto>(machineJson(buildingField = ""))
        assertEquals(null, withoutCategory.category)
    }
}
