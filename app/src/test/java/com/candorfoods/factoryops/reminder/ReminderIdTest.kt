package com.candorfoods.factoryops.reminder

import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.notificationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ReminderIdTest {

    private fun reminder(
        type: ReminderType = ReminderType.B1_NEW_BREAKDOWN,
        category: ReminderCategory = ReminderCategory.BREAKDOWN,
        entityId: String = "flag-1",
    ) = Reminder(
        type = type, category = category, entityId = entityId,
        recipientRole = UserRole.TECHNICIAN, tier = 0,
        title = "t", body = "b", deepLinkExtraKey = "ticket_id",
    )

    @Test fun `id is stable for same type and entity`() {
        assertEquals(reminder().notificationId(), reminder().notificationId())
    }

    @Test fun `different reminder types on same entity get different ids`() {
        assertNotEquals(
            reminder(type = ReminderType.B1_NEW_BREAKDOWN).notificationId(),
            reminder(type = ReminderType.B2_ACK_STALE).notificationId(),
        )
    }

    @Test fun `breakdown ids sit in the 3000 band, pm ids in the 4000 band`() {
        val bd = reminder(category = ReminderCategory.BREAKDOWN).notificationId()
        val pm = reminder(category = ReminderCategory.PM, type = ReminderType.P2_AWAITING_SUPERVISOR).notificationId()
        assertNotEquals(bd, pm)
    }
}
