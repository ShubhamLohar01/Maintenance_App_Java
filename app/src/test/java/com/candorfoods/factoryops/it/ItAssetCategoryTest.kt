package com.candorfoods.factoryops.it

import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.matches
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItAssetCategoryTest {

    private fun asset(category: String) = MtMachine(
        assetId = "A185-0001", building = "A-185", assetName = "Sample",
        category = category, subLocation = "1st Floor", ratedKw = null,
        powerLoadRaw = null, section = MachineCategory.OTHERS,
    )

    @Test fun `backend values are the exact mt_asset_list strings`() {
        assertEquals("Electric Asset", ItAssetCategory.ELECTRICAL.backendValue)
        assertEquals("IT/Communication", ItAssetCategory.IT_COMMUNICATION.backendValue)
    }

    @Test fun `matching an asset is trim and case insensitive`() {
        assertTrue(ItAssetCategory.ELECTRICAL.matches(asset("Electric Asset")))
        assertTrue(ItAssetCategory.ELECTRICAL.matches(asset("  electric asset ")))
        assertTrue(ItAssetCategory.IT_COMMUNICATION.matches(asset("it/communication")))
    }

    @Test fun `a category does not match a different category`() {
        assertFalse(ItAssetCategory.ELECTRICAL.matches(asset("IT/Communication")))
        assertFalse(ItAssetCategory.IT_COMMUNICATION.matches(asset("Production Equipment")))
        assertFalse(ItAssetCategory.ELECTRICAL.matches(asset("")))
    }

    @Test fun `of parses an enum name and falls back to ELECTRICAL`() {
        assertEquals(ItAssetCategory.ELECTRICAL, ItAssetCategory.of("ELECTRICAL"))
        assertEquals(ItAssetCategory.IT_COMMUNICATION, ItAssetCategory.of("IT_COMMUNICATION"))
        assertEquals(ItAssetCategory.ELECTRICAL, ItAssetCategory.of(null))
        assertEquals(ItAssetCategory.ELECTRICAL, ItAssetCategory.of("nonsense"))
    }
}
