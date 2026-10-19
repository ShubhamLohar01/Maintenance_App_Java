package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.isMeterlessFloor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Floors hidden from the Electric Consumption round because they have no meter.
 *
 * sub_location is hand-typed in pgAdmin, so the match has to survive the spellings that
 * actually turn up rather than only the tidy one.
 */
class MeterlessFloorTest {

    @Test fun `the meterless areas are hidden however they are spelled`() {
        listOf(
            "Canteen", "canteen", " CANTEEN ",
            "Back Side Gate", "back-side gate", "BackSideGate", "backside gate",
            "Entry Gate", "entry gate", "ENTRY-GATE",
            "Lab", "lab", "Laboratory", "Lab Floor",
        ).forEach { assertTrue(isMeterlessFloor(it), "should be hidden: $it") }
    }

    /** The real production floors must keep their rows — hiding one loses a day's reading. */
    @Test fun `metered floors are untouched`() {
        listOf(
            "Lower Basement", "Upper Basement Floor", "Service Floor", "Ground Floor",
            "1st Floor", "2nd Floor", "1st Mezzanine", "Terrace", "Store Area",
            "External Premises", "Packing area", "Mezzanine floor",
        ).forEach { assertFalse(isMeterlessFloor(it), "should NOT be hidden: $it") }
    }

    /** "lab" is matched as a whole name, not a fragment — a labelling area still reads. */
    @Test fun `a name merely containing lab is not hidden`() {
        listOf("Labelling Area", "Label Store", "Lab Equipment Floor").forEach {
            assertFalse(isMeterlessFloor(it), "should NOT be hidden: $it")
        }
    }
}
