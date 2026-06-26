package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.FloorReadingRowInDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsResponseDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitRequestDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitResponseDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloorReadingDtoTest {
    // Mirrors NetworkModule's Json (encodeDefaults stays off → null reading_date omitted).
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test fun `system response parses snake_case keys`() {
        val r = json.decodeFromString<FloorReadingsResponseDto>(
            """{"building":"A-185","reading_date":"2026-06-25","floors":[
                 {"floor":"1st floor","system_reading":88.3,"meter_reading":90.0},
                 {"floor":"2nd floor","system_reading":0.0,"meter_reading":null}]}"""
        )
        assertEquals("A-185", r.building)
        assertEquals("2026-06-25", r.readingDate)
        assertEquals(2, r.floors.size)
        assertEquals(88.3, r.floors[0].systemReading)
        assertEquals(90.0, r.floors[0].meterReading)
        assertEquals(0.0, r.floors[1].systemReading)   // idle floor
        assertNull(r.floors[1].meterReading)            // never saved
    }

    @Test fun `submit request echoes building, sends floor + meter_reading, never system_reading`() {
        val s = json.encodeToString(
            FloorReadingsSubmitRequestDto(
                readingDate = null,
                building = "A-185",
                rows = listOf(FloorReadingRowInDto(floor = "1st floor", meterReading = 90.0)),
            )
        )
        assertTrue(s.contains("\"building\":\"A-185\""), s)   // echoed from the GET → makes HEAD POST work
        assertTrue(s.contains("\"floor\":\"1st floor\""), s)
        assertTrue(s.contains("\"meter_reading\":90.0"), s)
        assertFalse(s.contains("system_reading"), s)
        // null date is "today" → key must be omitted, not sent as null
        assertFalse(s.contains("reading_date"), s)
    }

    @Test fun `submit request includes reading_date when provided`() {
        val s = json.encodeToString(
            FloorReadingsSubmitRequestDto(
                readingDate = "2026-06-24",
                building = "A-185",
                rows = listOf(FloorReadingRowInDto(floor = "1st floor", meterReading = 12.0)),
            )
        )
        assertTrue(s.contains("\"reading_date\":\"2026-06-24\""), s)
    }

    @Test fun `submit response parses saved count`() {
        val r = json.decodeFromString<FloorReadingsSubmitResponseDto>(
            """{"building":"A-185","reading_date":"2026-06-25","saved":3}"""
        )
        assertEquals("A-185", r.building)
        assertEquals(3, r.saved)
    }
}
