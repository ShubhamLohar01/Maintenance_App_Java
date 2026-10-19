package com.candorfoods.factoryops.tickets

import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.model.tickets.TicketRecipient
import com.candorfoods.factoryops.domain.model.tickets.TicketStatus
import com.candorfoods.factoryops.navigation.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.OffsetDateTime

class RaisedTicketModelTest {

    @ParameterizedTest
    @CsvSource(
        "open, OPEN",
        "acknowledged, ACKNOWLEDGED",
        "resolved, RESOLVED",
        "RESOLVED, RESOLVED",
        "' open ', OPEN",
    )
    fun `status parses the backend vocabulary`(raw: String, expected: TicketStatus) {
        assertEquals(expected, TicketStatus.of(raw))
    }

    @Test fun `an unknown status falls back rather than crashing`() {
        // The backend owns this vocabulary; a new value must not crash an old build.
        assertEquals(TicketStatus.OPEN, TicketStatus.of("triaged"))
        assertEquals(TicketStatus.OPEN, TicketStatus.of(null))
    }

    @Test fun `asset and area are joined for display`() {
        assertEquals("Laptop — office", ticket().assetWithArea)
    }

    @Test fun `a missing area does not leave a dangling dash`() {
        assertEquals("Laptop", ticket().copy(area = null).assetWithArea)
        assertEquals("Laptop", ticket().copy(area = "  ").assetWithArea)
    }

    @Test fun `a ticket with neither reads sensibly`() {
        assertEquals("Unspecified asset", ticket().copy(asset = null, area = null).assetWithArea)
    }

    @Test fun `corrective action comes from whoever closed it`() {
        val t = ticket().copy(
            recipients = listOf(
                TicketRecipient(1, "Someone", "pending", null, null, null, null),
                TicketRecipient(2, "Vipul ware", "done", "Vipul ware", "x", "Replaced the cable", null),
            )
        )
        assertEquals("Replaced the cable", t.correctiveAction)
    }

    @Test fun `corrective action is null while nobody has closed it`() {
        assertNull(ticket().correctiveAction)
    }

    @Test fun `age reads in human units`() {
        val created = OffsetDateTime.parse("2026-08-01T10:00:00+05:30")
        val t = ticket().copy(createdAt = created.toString())

        assertEquals("just now", t.age(created.plusSeconds(30)))
        assertEquals("45m ago", t.age(created.plusMinutes(45)))
        assertEquals("3h ago", t.age(created.plusHours(3)))
        assertEquals("2d ago", t.age(created.plusDays(2)))
    }

    @Test fun `a future timestamp from clock skew does not read as negative`() {
        val created = OffsetDateTime.parse("2026-08-01T10:00:00+05:30")
        val t = ticket().copy(createdAt = created.toString())
        assertEquals("just now", t.age(created.minusMinutes(5)))
    }

    @Test fun `an unparseable timestamp degrades quietly`() {
        assertEquals("", ticket().copy(createdAt = "not-a-date").age())
        assertEquals("—", ticket().copy(createdAt = null).raisedAtDisplay)
    }

    @Test fun `the IST offset is preserved rather than shifted to device time`() {
        // 13:21 IST must display as 01:21 PM whatever zone the phone is in.
        val t = ticket().copy(createdAt = "2026-08-01T13:21:17+05:30")
        assertEquals("01 Aug 2026, 01:21 PM", t.raisedAtDisplay)
    }

    @Test fun `detail route carries the ticket id`() {
        assertEquals("tickets/raised/42", Screen.RaisedTicketDetail.createRoute(42))
    }
}
