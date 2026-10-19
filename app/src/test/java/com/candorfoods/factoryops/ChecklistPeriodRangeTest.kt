package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.ChecklistRecordItem
import com.candorfoods.factoryops.presentation.checklist.ChecklistRow
import com.candorfoods.factoryops.presentation.checklist.ChecklistType
import com.candorfoods.factoryops.presentation.checklist.ChecklistUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Per-machine Date From / Date To on the Quarterly preventive forms.
 *
 * The maintenance date lives on each MACHINE, not on the sheet: one quarterly sheet
 * covers 161 machines serviced on different days, so a single document-level period
 * could never describe them. The backend derives the sheet's span from these and does
 * NOT require them (old builds send one `equipment_date`), so `canSubmit` here is what
 * actually makes the range mandatory.
 */
class ChecklistPeriodRangeTest {

    private fun rows(vararg dates: Pair<String, String>) = dates.mapIndexed { i, (from, to) ->
        ChecklistRow(
            section = "S", equipment = "Machine $i", srNo = i + 1,
            equipmentDate = from, equipmentDateTo = to, checkpoint = "cp",
            checked = true,
        )
    }

    private fun state(type: ChecklistType, vararg dates: Pair<String, String>) =
        ChecklistUiState(type = type, rows = rows(*dates))

    // ─── Which forms ask for a range ─────────────────────────────────────────────

    @Test fun `both quarterly forms date each machine over a range`() {
        assertTrue(state(ChecklistType.QUARTERLY).needsEquipmentRange)
        assertTrue(state(ChecklistType.A185_QUARTERLY).needsEquipmentRange)
    }

    @Test fun `monthly forms keep a single date per machine`() {
        assertFalse(state(ChecklistType.MONTHLY).needsEquipmentRange)
        assertFalse(state(ChecklistType.A185_MONTHLY).needsEquipmentRange)
    }

    // ─── Counting dated machines ─────────────────────────────────────────────────

    @Test fun `quarterly counts a machine only once both ends are valid`() {
        val s = state(
            ChecklistType.QUARTERLY,
            "2026-07-01" to "2026-09-30",   // complete
            "2026-07-05" to "",             // no end yet
            "" to "",                       // untouched
        )
        assertEquals(3, s.totalEquipment)
        assertEquals(1, s.datedEquipment)
        assertFalse(s.allEquipmentDated)
    }

    @Test fun `monthly ignores the empty end date`() {
        val s = state(ChecklistType.MONTHLY, "2026-07-09" to "", "2026-07-10" to "")
        assertEquals(2, s.datedEquipment)
        assertTrue(s.allEquipmentDated)
    }

    @Test fun `an inverted range does not count as dated`() {
        val s = state(ChecklistType.QUARTERLY, "2026-09-30" to "2026-07-01")
        assertEquals(0, s.datedEquipment)
    }

    @Test fun `equal dates are a valid one-day range`() {
        // What a machine serviced within a single day looks like, and what monthly
        // submissions produce for both ends.
        assertEquals(1, state(ChecklistType.QUARTERLY, "2026-07-01" to "2026-07-01").datedEquipment)
    }

    @Test fun `string compare orders dates across a year boundary`() {
        // ISO yyyy-MM-dd sorts lexicographically the same way it sorts chronologically —
        // the property the comparison relies on.
        assertEquals(1, state(ChecklistType.QUARTERLY, "2026-12-01" to "2027-01-31").datedEquipment)
        assertEquals(0, state(ChecklistType.QUARTERLY, "2027-01-31" to "2026-12-01").datedEquipment)
    }

    // ─── Per-machine error text ──────────────────────────────────────────────────

    private val q = ChecklistUiState(type = ChecklistType.QUARTERLY)
    private val m = ChecklistUiState(type = ChecklistType.MONTHLY)

    @Test fun `inverted range says which way round it goes`() {
        assertEquals(
            "Date To must be on or after Date From",
            q.equipmentDateError("2026-09-30", "2026-07-01"),
        )
    }

    @Test fun `a malformed date reports the format`() {
        assertNotNull(q.equipmentDateError("01-07-2026", "2026-09-30"))
        assertTrue(q.equipmentDateError("01-07-2026", "2026-09-30")!!.contains("YYYY-MM-DD"))
        assertNotNull(q.equipmentDateError("2026-07-01", "30-09-2026"))
    }

    @Test fun `a half filled range is incomplete rather than wrong`() {
        // No red text while the user is still typing the second field — only once both
        // are present can they actually contradict each other.
        assertNull(q.equipmentDateError("2026-07-01", ""))
        assertNull(q.equipmentDateError("", ""))
    }

    @Test fun `monthly never complains about a missing end date`() {
        assertNull(m.equipmentDateError("2026-07-09", ""))
        // ...but still catches a malformed start.
        assertNotNull(m.equipmentDateError("09-07-2026", ""))
    }

    // ─── Read-back ───────────────────────────────────────────────────────────────

    private fun item(from: String, to: String) = ChecklistRecordItem(
        section = "S", equipment = "Pump", srNo = 1,
        equipmentDate = from, equipmentDateTo = to,
        checkpoint = "cp", status = "OK", remarks = "",
    )

    @Test fun `a stored machine keeps both ends`() {
        val it = item("2026-07-01", "2026-09-30")
        assertEquals("2026-07-01", it.equipmentDate)
        assertEquals("2026-09-30", it.equipmentDateTo)
    }

    @Test fun `a legacy item with no end date still reads`() {
        assertEquals("", item("2026-07-09", "").equipmentDateTo)
    }
}
