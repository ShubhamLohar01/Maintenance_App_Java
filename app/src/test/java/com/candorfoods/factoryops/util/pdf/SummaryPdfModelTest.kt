package com.candorfoods.factoryops.util.pdf

import com.candorfoods.factoryops.domain.model.reports.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SummaryPdfModelTest {

    private fun report() = SummaryReport(
        periodFrom = "2026-08-01", periodTo = "2026-08-28",
        plants = listOf("A-185", "W-202"), generatedAt = 0L, generatedBy = "Sup",
        readings = ReadingsSummary(
            windowDays = 28, totalEntries = 100,
            sources = listOf(ReadingSource("Gas", "mt_utility_gas", 10, 8, 20)),
        ),
        pm = PmSummary(plansActive = 5, overdueTotal = 3),
        breakdowns = BreakdownSummary(raisedInPeriod = 2, stillOpen = 1),
        people = PeopleSummary(totalUsers = 17, activeUsers = 6),
        assets = AssetSummary(total = 877, beforeTracking = 877),
        notes = listOf("a note"),
    )

    @Test
    fun `builds one section per report area`() {
        val model = SummaryPdfModel.from(report())
        assertEquals(
            listOf(
                "1. Daily readings", "2. Preventive maintenance", "3. Breakdowns",
                "4. App usage by person", "5. Asset register",
            ),
            model.sections.map { it.title },
        )
    }

    @Test
    fun `kpi band carries the headline numbers`() {
        val kpis = SummaryPdfModel.from(report()).kpis.associate { it.label to it.value }
        assertEquals("100", kpis["Readings logged"])
        assertEquals("3", kpis["PM overdue"])
        assertEquals("1", kpis["Breakdowns open"])
        assertEquals("6 of 17", kpis["Active users"])
    }

    @Test
    fun `every table row has one cell per column weight`() {
        for (section in SummaryPdfModel.from(report()).sections) {
            for (table in section.tables) {
                assertEquals(table.headers.size, table.weights.size)
                table.rows.forEach { assertEquals(table.headers.size, it.size) }
            }
        }
    }

    @Test
    fun `notes are carried through so the pdf can print them`() {
        assertTrue(SummaryPdfModel.from(report()).notes.contains("a note"))
    }
}
