package com.candorfoods.factoryops.machine

import com.candorfoods.factoryops.domain.model.CONDITION_SHUT_DOWN
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.isShutDown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MtMachineShutDownTest {

    private fun machine(condition: String?) = MtMachine(
        assetId = "W202-1", building = "W-202", assetName = "Band Sealer",
        category = "Production Equipment", subLocation = "Floor 1", ratedKw = 1.2,
        powerLoadRaw = "120Watt", section = MachineCategory.PRODUCTION, condition = condition,
    )

    @Test fun `sentinel is the canonical spelling`() {
        assertEquals("Shut Down", CONDITION_SHUT_DOWN)
    }

    @Test fun `isShutDown true for the sentinel regardless of case or whitespace`() {
        assertTrue(machine("Shut Down").isShutDown())
        assertTrue(machine("shut down").isShutDown())
        assertTrue(machine("  SHUT DOWN  ").isShutDown())
    }

    @Test fun `isShutDown false for other conditions, blank, or null`() {
        assertFalse(machine("Good").isShutDown())
        assertFalse(machine("").isShutDown())
        assertFalse(machine(null).isShutDown())
    }
}
