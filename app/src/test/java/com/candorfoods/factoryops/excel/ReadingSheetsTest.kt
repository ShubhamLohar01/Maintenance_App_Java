package com.candorfoods.factoryops.excel

import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.model.utilities.UtilityRecord
import com.candorfoods.factoryops.util.excel.Cell
import com.candorfoods.factoryops.util.excel.ReadingSheets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Each Daily Reading section's records, mapped to the table that gets exported. */
class ReadingSheetsTest {

    // ─── Utilities: the columns come from the data, not a hardcoded list ──────────

    private val diesel = listOf(
        UtilityRecord(
            plant = "W-202", readingDate = "2026-08-21",
            numbers = mapOf("qty_litres" to 420.0, "cost" to 38640.0),
            texts = mapOf("remark" to "topped up"),
        ),
        UtilityRecord(
            plant = "A-185", readingDate = "2026-08-20",
            numbers = mapOf("qty_litres" to 385.0),   // no cost recorded that day
            texts = emptyMap(),
        ),
    )

    @Test fun `utility columns are the union of every record's keys`() {
        val sheet = ReadingSheets.utility("Diesel consumption", diesel)
        assertEquals(listOf("Date", "Plant", "Cost", "Qty litres", "Remark"), sheet.headers)
    }

    @Test fun `a value missing on one record leaves a blank cell, not a zero`() {
        val sheet = ReadingSheets.utility("Diesel consumption", diesel)
        val costColumn = sheet.headers.indexOf("Cost")
        assertEquals(Cell.Num(38640.0), sheet.rows[0][costColumn])
        // A day with no cost must not total as 0 — that would understate the average.
        assertEquals(Cell.Empty, sheet.rows[1][costColumn])
    }

    @Test fun `raw field names are humanised for the header row`() {
        assertEquals("Qty litres", ReadingSheets.humanise("qty_litres"))
        assertEquals("Meter reading", ReadingSheets.humanise("meterReading"))
        assertEquals("Cost", ReadingSheets.humanise("cost"))
    }

    @Test fun `an empty section produces headers and no rows`() {
        val sheet = ReadingSheets.utility("Water consumption", emptyList())
        assertEquals(listOf("Date", "Plant"), sheet.headers)
        assertTrue(sheet.rows.isEmpty())
    }

    // ─── Floor readings: one table, split between two sections ────────────────────

    private val floorRows = listOf(
        FloorReadingReportRow("2026-08-21", "W-202", "Lower Basement", 4120.0, 412.5),
        FloorReadingReportRow("2026-08-21", "W-202", "AC · Server Room AC", 880.0, 0.0),
        FloorReadingReportRow("2026-08-20", "W-202", "Service Floor", 2080.0, 208.0),
        FloorReadingReportRow("2026-08-20", "W-202", "AC · UPS Room AC 1", 445.0, 0.0),
    )

    @Test fun `the electric export excludes AC rows`() {
        val sheet = ReadingSheets.floorReadings("Electric consumption", floorRows, acOnly = false)
        val names = sheet.rows.map { (it[2] as Cell.Text).value }
        assertEquals(listOf("Lower Basement", "Service Floor"), names)
        assertEquals("Floor", sheet.headers[2])
    }

    @Test fun `the AC export keeps only AC rows, with the prefix stripped`() {
        val sheet = ReadingSheets.floorReadings("AC reading", floorRows, acOnly = true)
        val names = sheet.rows.map { (it[2] as Cell.Text).value }
        assertEquals(listOf("Server Room AC", "UPS Room AC 1"), names)
        assertEquals("AC unit", sheet.headers[2])
        assertFalse(names.any { it.startsWith("AC ·") }, "the storage prefix is internal")
    }

    @Test fun `rows come out newest first`() {
        val sheet = ReadingSheets.floorReadings("Electric consumption", floorRows, acOnly = false)
        assertEquals(Cell.date("2026-08-21"), sheet.rows.first()[0])
        assertEquals(Cell.date("2026-08-20"), sheet.rows.last()[0])
    }

    @Test fun `the difference column is actual minus system`() {
        val sheet = ReadingSheets.floorReadings("Electric consumption", floorRows, acOnly = false)
        assertEquals(Cell.Num(4120.0 - 412.5), sheet.rows[0][5])
    }

    @Test fun `a missing reading leaves the difference blank rather than guessing`() {
        val partial = listOf(FloorReadingReportRow("2026-08-21", "W-202", "Terrace", null, 88.0))
        val sheet = ReadingSheets.floorReadings("Electric consumption", partial, acOnly = false)
        assertEquals(Cell.Empty, sheet.rows[0][3])
        assertEquals(Cell.Empty, sheet.rows[0][5])
    }
}
