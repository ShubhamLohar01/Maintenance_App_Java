package com.candorfoods.factoryops

import com.candorfoods.factoryops.util.canonicalPlant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * plantId reaches the app as whatever was typed into `mt_users.location` — in practice
 * the unhyphenated "A185"/"W202". Screens that gate on plant compare against the
 * hyphenated canonical form, so this normalisation is what keeps the A-185-only
 * Daily Reading card visible to A-185 technicians.
 */
class PlantIdTest {

    @Test fun `unhyphenated codes canonicalise`() {
        assertEquals("A-185", canonicalPlant("A185"))
        assertEquals("W-202", canonicalPlant("W202"))
    }

    @Test fun `already canonical values are unchanged`() {
        assertEquals("A-185", canonicalPlant("A-185"))
        assertEquals("W-202", canonicalPlant("W-202"))
    }

    @Test fun `case whitespace and separators are tolerated`() {
        assertEquals("A-185", canonicalPlant(" a-185 "))
        assertEquals("A-185", canonicalPlant("a185"))
        assertEquals("A-185", canonicalPlant("A 185"))
        assertEquals("W-202", canonicalPlant("w_202"))
    }

    @Test fun `null and blank become empty, never a false match`() {
        assertEquals("", canonicalPlant(null))
        assertEquals("", canonicalPlant("   "))
    }

    @Test fun `hand-typed location with extra words still resolves`() {
        assertEquals("A-185", canonicalPlant("A-185 Unit 2"))
        assertEquals("W-202", canonicalPlant("Warehouse W202"))
    }

    @Test fun `unknown site code is passed through trimmed`() {
        assertEquals("B-300", canonicalPlant(" B-300 "))
    }
}
