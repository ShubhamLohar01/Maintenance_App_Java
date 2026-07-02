package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.notification.NotificationEnvelope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationEnvelopeTest {

    @Test fun `parse builds a Reminder from a complete data map`() {
        val data = mapOf(
            "type" to "B1_NEW_BREAKDOWN",
            "category" to "BREAKDOWN",
            "entityId" to "flag-9",
            "targetRole" to "TECHNICIAN",
            "tier" to "2",
            "title" to "Breakdown",
            "body" to "Needs a tech",
            "deepLinkExtraKey" to "ticket_id",
        )
        val r = NotificationEnvelope.parse(data)!!
        assertEquals(ReminderType.B1_NEW_BREAKDOWN, r.type)
        assertEquals(ReminderCategory.BREAKDOWN, r.category)
        assertEquals("flag-9", r.entityId)
        assertEquals(UserRole.TECHNICIAN, r.recipientRole)
        assertEquals(2, r.tier)
        assertEquals("ticket_id", r.deepLinkExtraKey)
    }

    @Test fun `parse returns null on missing or bad fields`() {
        assertNull(NotificationEnvelope.parse(emptyMap()))
        assertNull(NotificationEnvelope.parse(mapOf("type" to "NOT_A_TYPE", "category" to "PM", "entityId" to "x")))
    }
}
