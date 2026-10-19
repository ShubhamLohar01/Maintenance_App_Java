package com.candorfoods.factoryops.it

import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.navigation.Screen
import com.candorfoods.factoryops.presentation.dashboard.DashboardAction
import com.candorfoods.factoryops.presentation.dashboard.dashboardTilesFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ItDashboardTilesTest {

    @Test fun `IT technician gets Raised Tickets and IT Assets`() {
        val tiles = dashboardTilesFor(UserRole.IT_TECHNICIAN)
        assertEquals(listOf("Raised Tickets", "IT Assets"), tiles.map { it.label })
    }

    @Test fun `the IT Assets tile opens the category chooser and carries no badge`() {
        val tile = dashboardTilesFor(UserRole.IT_TECHNICIAN).single { it.label == "IT Assets" }
        assertEquals(
            DashboardAction.Open(Screen.ItAssetCategories.route),
            tile.action,
        )
        assertNull(tile.badge)
    }

    @Test fun `the Raised Tickets tile opens the ticket list`() {
        val tile = dashboardTilesFor(UserRole.IT_TECHNICIAN).single { it.label == "Raised Tickets" }
        assertEquals(DashboardAction.Open(Screen.RaisedTickets.route), tile.action)
        assertNull(tile.badge)
    }

    @Test fun `other roles' tile sets are untouched`() {
        assertEquals(8, dashboardTilesFor(UserRole.TECHNICIAN).size)
        assertEquals(9, dashboardTilesFor(UserRole.SUPERVISOR).size)
        assertEquals(10, dashboardTilesFor(UserRole.HEAD).size)
        assertEquals(0, dashboardTilesFor(UserRole.OPERATOR).size)
    }

    @Test fun `the asset list route carries the category enum name`() {
        assertEquals(
            "it/assets/IT_COMMUNICATION",
            Screen.ItAssetList.createRoute(ItAssetCategory.IT_COMMUNICATION),
        )
        assertEquals(
            "it/assets/ELECTRICAL",
            Screen.ItAssetList.createRoute(ItAssetCategory.ELECTRICAL),
        )
    }
}
