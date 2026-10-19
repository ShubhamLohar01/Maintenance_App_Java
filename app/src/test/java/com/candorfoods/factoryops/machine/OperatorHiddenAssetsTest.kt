package com.candorfoods.factoryops.machine

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.isHiddenFromOperator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperatorHiddenAssetsTest {

    private fun asset(category: String, subLocation: String = "1st Floor") = MtMachine(
        assetId = "X-1", building = "W-202", assetName = "Sample",
        category = category, subLocation = subLocation, ratedKw = null,
        powerLoadRaw = null, section = MachineCategory.OTHERS,
    )

    @Test fun `all non-production support categories are hidden from operators`() {
        listOf(
            "Electric Asset", "Lab Equipment", "Utility/Infrastructure", "Housekeeping",
            "Furniture/Storage", "Infrastructure", "IT/Communication",
        ).forEach { c ->
            assertTrue(asset(category = c).isHiddenFromOperator(), "expected '$c' hidden")
        }
    }

    @Test fun `matching is case and whitespace insensitive`() {
        assertTrue(asset(category = "  electric asset  ").isHiddenFromOperator())
        assertTrue(asset(category = "it/communication").isHiddenFromOperator())
    }

    @Test fun `production equipment stays visible`() {
        assertFalse(asset(category = "Production Equipment", subLocation = "2nd Floor").isHiddenFromOperator())
    }
}
