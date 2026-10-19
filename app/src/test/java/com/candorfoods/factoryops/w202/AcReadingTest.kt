package com.candorfoods.factoryops.w202

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.w202.AC_FLOOR_PREFIX
import com.candorfoods.factoryops.domain.model.w202.acFloorKey
import com.candorfoods.factoryops.domain.model.w202.acReadingPoints
import com.candorfoods.factoryops.domain.model.w202.isIndoorAcAsset
import com.candorfoods.factoryops.domain.model.w202.isAcFloorKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Daily AC readings: which register rows count as an air conditioner, and how they are
 * keyed into mt_floor_utility_readings.
 */
class AcReadingTest {

    private fun asset(
        id: String,
        name: String,
        building: String = "W-202",
        floor: String = "Service Floor",
    ) = MtMachine(
        assetId = id, building = building, assetName = name,
        category = "Electric Asset", subLocation = floor, ratedKw = null,
        powerLoadRaw = null, section = MachineCategory.OTHERS,
    )

    // ─── Recognising an AC by name ────────────────────────────────────────────────

    @Test fun `indoor units are recognised however they are written`() {
        listOf("AC Indoor", "ac indoor", "AC  Indoor", " A/C Indoor ").forEach {
            assertTrue(isIndoorAcAsset(it), "should be an indoor AC: $it")
        }
    }

    /** Only the indoor unit is read. The register's other AC entries are not metered. */
    @Test fun `the other AC varieties are not read`() {
        listOf("Air Conditioner", "Air conditioner", "Ductable AC", "AC Out Door", "VRV System")
            .forEach { assertFalse(isIndoorAcAsset(it), "should not be read: $it") }
    }

    @Test fun `words merely containing ac are not indoor units`() {
        listOf("Rack Server", "Packaging Machine", "Track Roller", "AC Indoor Panel Board")
            .forEach { assertFalse(isIndoorAcAsset(it), "should not be read: $it") }
    }

    // ─── Building the list ────────────────────────────────────────────────────────

    private val register = listOf(
        asset("W202-0192", "AC Indoor", floor = "Service Floor"),
        asset("W202-0170", "AC Indoor", floor = "Lower Basement"),
        asset("W202-0078", "Air Conditioner", floor = "Service Floor"),
        asset("W202-0200", "Ductable AC", floor = "Service Floor"),
        asset("W202-0269", "AC Out Door", floor = "TERRACE"),
        asset("W202-0101", "Rack Server", floor = "Service Floor"),
        asset("A185-0200", "AC Indoor", building = "A-185"),
        asset("W202-0104", "  ", floor = "Terrace"),
    )

    /**
     * Every indoor unit shares the name "AC Indoor", and the reading is keyed by name, so
     * the whole building collapses to a single row — one AC Indoor reading per day.
     */
    @Test fun `only the indoor ACs are listed, and they collapse to one row`() {
        val points = register.acReadingPoints("W-202")
        assertEquals(listOf("AC Indoor"), points.map { it.name })
    }

    @Test fun `the other plant's ACs are excluded`() {
        assertEquals(listOf("AC Indoor"), register.acReadingPoints("A-185").map { it.name })
        assertEquals(listOf("A185-0200"), register.acReadingPoints("A-185").map { it.assetId })
    }

    @Test fun `two ACs sharing a name collapse to one row`() {
        // They would write to the same (building, floor, date) key, so a second row
        // would silently overwrite the first.
        val dupes = listOf(
            asset("W202-0300", "AC Indoor"),
            asset("W202-0301", "AC Indoor"),
        )
        assertEquals(1, dupes.acReadingPoints("W-202").size)
    }

    // ─── How a reading is keyed into the table ────────────────────────────────────

    @Test fun `an AC row is stored under a prefixed floor key`() {
        val point = register.acReadingPoints("W-202").first { it.name == "AC Indoor" }
        assertEquals("${AC_FLOOR_PREFIX}AC Indoor", point.floorKey)
        assertTrue(isAcFloorKey(point.floorKey))
    }

    /** The prefix is what stops an AC named like a floor from overwriting that floor. */
    @Test fun `a real floor is never mistaken for an AC row`() {
        listOf("Service Floor", "Lower Basement", "Terrace").forEach {
            assertFalse(isAcFloorKey(it), "should not look like an AC key: $it")
        }
        assertFalse(acFloorKey("Terrace") == "Terrace")
    }
}
