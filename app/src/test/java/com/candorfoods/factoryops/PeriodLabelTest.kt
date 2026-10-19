package com.candorfoods.factoryops

import com.candorfoods.factoryops.util.PeriodLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The register's period label, replacing the blank "Date ·" an auto-generated
 *  document produced (checklist_date is deliberately empty on those). */
class PeriodLabelTest {

    @Test
    fun `a monthly span reads as one month`() {
        assertEquals("Sept 2026", PeriodLabel.of("2026-09-01", "2026-09-30"))
    }

    @Test
    fun `a quarterly span names both ends and the year once, keeping Sept`() {
        assertEquals("Jul–Sept 2026", PeriodLabel.of("2026-07-01", "2026-09-30"))
    }

    @Test
    fun `a span crossing new year spells out both years`() {
        assertEquals("Dec 2026–Feb 2027", PeriodLabel.of("2026-12-01", "2027-02-28"))
    }

    @Test
    fun `only a from date still yields its month`() {
        assertEquals("Aug 2026", PeriodLabel.of("2026-08-14", ""))
    }

    @Test
    fun `only a to date still yields its month`() {
        assertEquals("Aug 2026", PeriodLabel.of(null, "2026-08-14"))
    }

    @Test
    fun `falls back to the record's own date when the range is blank`() {
        assertEquals("Jun 2026", PeriodLabel.of("", "", "2026-06-20"))
    }

    @Test
    fun `an unparseable fallback is shown as typed rather than swallowed`() {
        assertEquals("20-06-2026", PeriodLabel.of("", "", "20-06-2026"))
    }

    @Test
    fun `nothing at all reads as a dash, never an empty gap`() {
        assertEquals("—", PeriodLabel.of("", "", ""))
        assertEquals("—", PeriodLabel.of(null, null, null))
    }

    @Test
    fun `a malformed range does not throw`() {
        assertEquals("—", PeriodLabel.of("not-a-date", "2026-13-45"))
    }

    @Test
    fun `September uses the printed form's Sept, not Java's Sep`() {
        assertEquals("Sept 2026", PeriodLabel.of("2026-09-08", "2026-09-08"))
    }
}
