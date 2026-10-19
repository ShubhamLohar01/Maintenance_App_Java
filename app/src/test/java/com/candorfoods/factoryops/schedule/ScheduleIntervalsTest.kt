package com.candorfoods.factoryops.schedule

import com.candorfoods.factoryops.data.remote.dto.AssetScheduleDto
import com.candorfoods.factoryops.data.remote.dto.AssetScheduleIntervalDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.ScheduleInterval
import com.candorfoods.factoryops.presentation.schedule.ScheduleEditorState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Split-shift schedules: several recording windows on the same day, repeating on every
 * selected weekday. 600 = 10:00, 780 = 13:00, 900 = 15:00, 1350 = 22:30.
 */
class ScheduleIntervalsTest {

    private fun dto(
        startMin: Int? = null,
        endMin: Int? = null,
        intervals: List<AssetScheduleIntervalDto>? = null,
    ) = AssetScheduleDto(
        assetId = "W202-0005", assetName = "Dryer Belt", building = "W-202",
        subLocation = "Ground floor", startMin = startMin, endMin = endMin,
        intervals = intervals, active = true, hours = 0.0,
    )

    private fun editor(vararg intervals: ScheduleInterval, is24h: Boolean = false) =
        ScheduleEditorState(
            assetId = "W202-0005", assetName = "Dryer Belt",
            intervals = intervals.toList(), active = true, hasExisting = true,
            days = setOf(1, 2, 3, 4, 5), is24h = is24h,
        )

    // ─── ScheduleInterval ─────────────────────────────────────────────────────────

    @Test fun `an interval measures its own span`() {
        assertEquals(3.0, ScheduleInterval(600, 780).hours)
        assertEquals(7.5, ScheduleInterval(900, 1350).hours)
    }

    @Test fun `an interval is valid only inside the day and in order`() {
        assertTrue(ScheduleInterval(600, 780).isValid)
        assertTrue(ScheduleInterval(0, 1440).isValid)
        assertFalse(ScheduleInterval(780, 600).isValid, "end before start")
        assertFalse(ScheduleInterval(600, 600).isValid, "zero length")
        assertFalse(ScheduleInterval(-1, 600).isValid, "before midnight")
        assertFalse(ScheduleInterval(600, 1441).isValid, "past midnight")
    }

    @Test fun `overlap is exclusive at the boundary so back-to-back windows are fine`() {
        assertTrue(ScheduleInterval(600, 900).overlaps(ScheduleInterval(780, 1000)))
        assertTrue(ScheduleInterval(600, 900).overlaps(ScheduleInterval(700, 800)), "contained")
        assertFalse(ScheduleInterval(600, 780).overlaps(ScheduleInterval(900, 1350)), "gap")
        assertFalse(ScheduleInterval(600, 780).overlaps(ScheduleInterval(780, 900)), "touching")
    }

    // ─── Reading the API ──────────────────────────────────────────────────────────

    @Test fun `a split shift comes back as its windows, sorted`() {
        val schedule = dto(
            startMin = 600, endMin = 1350,
            intervals = listOf(
                AssetScheduleIntervalDto(900, 1350),
                AssetScheduleIntervalDto(600, 780),
            ),
        ).toDomain()

        assertEquals(
            listOf(ScheduleInterval(600, 780), ScheduleInterval(900, 1350)),
            schedule.intervals,
        )
        assertTrue(schedule.isSplitShift)
        assertTrue(schedule.hasSchedule)
    }

    /** The backend may predate split shifts and send only the single window. */
    @Test fun `a legacy single-window row reads as one interval`() {
        val schedule = dto(startMin = 600, endMin = 1200).toDomain()
        assertEquals(listOf(ScheduleInterval(600, 1200)), schedule.intervals)
        assertFalse(schedule.isSplitShift)
    }

    @Test fun `an unscheduled asset has no intervals`() {
        val schedule = dto().toDomain()
        assertTrue(schedule.intervals.isEmpty())
        assertFalse(schedule.hasSchedule)
    }

    @Test fun `a malformed interval is dropped rather than shown`() {
        val schedule = dto(
            startMin = 600, endMin = 780,
            intervals = listOf(AssetScheduleIntervalDto(780, 600), AssetScheduleIntervalDto(600, 780)),
        ).toDomain()
        assertEquals(listOf(ScheduleInterval(600, 780)), schedule.intervals)
    }

    // ─── Editor validation ────────────────────────────────────────────────────────

    @Test fun `a clean split shift validates and totals its hours`() {
        val ed = editor(ScheduleInterval(600, 780), ScheduleInterval(900, 1350))
        assertNull(ed.validationError)
        assertEquals(10.5, ed.totalHours)
    }

    @Test fun `overlapping windows are rejected and both are named`() {
        val ed = editor(ScheduleInterval(600, 900), ScheduleInterval(780, 1350))
        val error = ed.validationError
        assertNotNull(error)
        assertTrue(error!!.contains("10:00 AM"), error)
        assertTrue(error.contains("1:00 PM"), error)
    }

    @Test fun `overlap is detected even when the windows are entered out of order`() {
        val ed = editor(ScheduleInterval(780, 1350), ScheduleInterval(600, 900))
        assertNotNull(ed.validationError)
    }

    @Test fun `an end before its start is rejected`() {
        val ed = editor(ScheduleInterval(780, 600))
        assertTrue(ed.validationError!!.contains("End time must be after start time"))
    }

    @Test fun `a 24h run ignores the windows entirely`() {
        val ed = editor(ScheduleInterval(780, 600), is24h = true)
        assertNull(ed.validationError, "a bad window can't block an all-day run")
        assertEquals(24.0, ed.totalHours)
    }

    @Test fun `the last window cannot be removed`() {
        assertFalse(editor(ScheduleInterval(600, 780)).canRemoveInterval)
        assertTrue(editor(ScheduleInterval(600, 780), ScheduleInterval(900, 1350)).canRemoveInterval)
    }
}
