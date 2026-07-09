package com.candorfoods.factoryops.machine

import com.candorfoods.factoryops.data.remote.dto.toAddDto
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MtMachineToAddDtoTest {

    private fun machine() = MtMachine(
        assetId = "SHOULD-BE-IGNORED", building = "W-202", assetName = "New Roaster",
        category = "Production Equipment", subLocation = "Floor 2", ratedKw = 5.0,
        powerLoadRaw = "2.2 kW", section = MachineCategory.PRODUCTION, quantity = 3,
        condition = "Good", assignedTo = "Ravi", modelNo = "RX-9", serialNo = "SN-1",
        remarks = "spare belt in store",
    )

    @Test fun `toAddDto blanks asset_id and omits rated_kw, carries the rest`() {
        val dto = machine().toAddDto()
        assertEquals("", dto.assetId)          // backend assigns
        assertNull(dto.ratedKw)                // backend computes from power_load
        assertEquals("W-202", dto.building)
        assertEquals("New Roaster", dto.assetName)
        assertEquals("Production Equipment", dto.category)
        assertEquals("Floor 2", dto.subLocation)
        assertEquals("2.2 kW", dto.powerLoad)
        assertEquals(3, dto.quantity)
        assertEquals("Good", dto.condition)
        assertEquals("Ravi", dto.assignedTo)
        assertEquals("RX-9", dto.modelNo)
        assertEquals("SN-1", dto.serialNo)
        assertEquals("spare belt in store", dto.remarks)
    }
}
