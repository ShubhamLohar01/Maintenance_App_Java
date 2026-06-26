package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.repository.computeRunKwh
import com.candorfoods.factoryops.util.Constants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnergyRunComputeTest {

    @Test
    fun `kwh is rated times hours times power factor`() {
        val kwh = computeRunKwh(3.0, 0L, 3_600_000L)!!   // 1 hour
        assertEquals(3.0 * 1.0 * Constants.POWER_FACTOR, kwh, 1e-9)
    }

    @Test
    fun `null rating yields null kwh`() {
        assertNull(computeRunKwh(null, 0L, 3_600_000L))
    }
}
