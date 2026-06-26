package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.MtMachineDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.isRunnable
import com.candorfoods.factoryops.util.PowerLoad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the mt_asset_list mapping: power_load parsing, the category -> section
 * derivation, the new backend fields, and the runnable rule.
 */
class MtAssetMappingTest {

    // ---- PowerLoad.parseKw -------------------------------------------------

    @Test fun `parses watt text to kW`() {
        assertEquals(0.12, PowerLoad.parseKw("120Watt")!!, 1e-9)
        assertEquals(1.2, PowerLoad.parseKw("1200 W")!!, 1e-9)
    }

    @Test fun `parses kW text as-is`() {
        assertEquals(2.2, PowerLoad.parseKw("2.2 kW")!!, 1e-9)
        assertEquals(2.2, PowerLoad.parseKw("2.2kw")!!, 1e-9)
    }

    @Test fun `parses HP text converting to kW`() {
        assertEquals(1.5 * 0.7457, PowerLoad.parseKw("1.5 HP")!!, 1e-6)
    }

    @Test fun `returns null for absent placeholder or unit-less text`() {
        assertNull(PowerLoad.parseKw(null))
        assertNull(PowerLoad.parseKw(""))
        assertNull(PowerLoad.parseKw("NA"))
        assertNull(PowerLoad.parseKw("-"))
        assertNull(PowerLoad.parseKw("125"))
        assertNull(PowerLoad.parseKw("Split AC"))
    }

    // ---- DTO -> domain mapping --------------------------------------------

    private fun dto(
        assetId: String = "A185-0001",
        building: String = "A-185",
        assetName: String = "Roaster",
        category: String = "Production Equipment",
        subLocation: String = "Lower Basement",
        powerLoad: String? = "120Watt",
        ratedKw: Double? = null,
        condition: String? = null,
        assignedTo: String? = null,
    ) = MtMachineDto(
        assetId = assetId, building = building, assetName = assetName,
        category = category, subLocation = subLocation, powerLoad = powerLoad,
        ratedKw = ratedKw, condition = condition, assignedTo = assignedTo,
    )

    @Test fun `production equipment maps to PRODUCTION section`() {
        assertEquals(MachineCategory.PRODUCTION, dto(category = "Production Equipment").toDomain().section)
    }

    @Test fun `anything other than production equipment maps to OTHERS section`() {
        assertEquals(MachineCategory.OTHERS, dto(category = "Packaging").toDomain().section)
        assertEquals(MachineCategory.OTHERS, dto(category = "Electric Asset").toDomain().section)
        assertEquals(MachineCategory.OTHERS, dto(category = "").toDomain().section)
    }

    @Test fun `maps columns and parses ratedKw while keeping raw power text`() {
        val m = dto(powerLoad = "120Watt").toDomain()
        assertEquals("A185-0001", m.assetId)
        assertEquals("A-185", m.building)
        assertEquals("Roaster", m.assetName)
        assertEquals("Production Equipment", m.category)
        assertEquals("Lower Basement", m.subLocation)
        assertEquals(0.12, m.ratedKw!!, 1e-9)
        assertEquals("120Watt", m.powerLoadRaw)
    }

    @Test fun `prefers backend rated_kw over parsing power_load`() {
        val m = dto(powerLoad = "120Watt", ratedKw = 3.0).toDomain()
        assertEquals(3.0, m.ratedKw!!, 1e-9)
    }

    @Test fun `maps condition and assigned_to`() {
        val m = dto(condition = "Good", assignedTo = "Prod Team").toDomain()
        assertEquals("Good", m.condition)
        assertEquals("Prod Team", m.assignedTo)
    }

    @Test fun `null power load yields null ratedKw and null raw`() {
        val m = dto(powerLoad = null, ratedKw = null).toDomain()
        assertNull(m.ratedKw)
        assertNull(m.powerLoadRaw)
    }

    // ---- runnable rule -----------------------------------------------------

    @Test fun `runnable when power_load present and category not blocked`() {
        assertTrue(dto(category = "Packaging", powerLoad = "3 kW").toDomain().isRunnable())
        assertTrue(dto(category = "Roasting", powerLoad = "120Watt").toDomain().isRunnable())
    }

    @Test fun `not runnable without a power_load`() {
        assertFalse(dto(category = "Packaging", powerLoad = null).toDomain().isRunnable())
        assertFalse(dto(category = "Packaging", powerLoad = "  ").toDomain().isRunnable())
    }

    @Test fun `not runnable for blocked categories even with power_load`() {
        assertFalse(dto(category = "Electric Asset", powerLoad = "120Watt").toDomain().isRunnable())
        assertFalse(dto(category = "IT/Communication", powerLoad = "20 W").toDomain().isRunnable())
        assertFalse(dto(category = "Power Distribution", powerLoad = "5 kW").toDomain().isRunnable())
        assertFalse(dto(category = "electric asset", powerLoad = "120Watt").toDomain().isRunnable())
    }
}
