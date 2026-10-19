package com.candorfoods.factoryops.notification

import com.candorfoods.factoryops.domain.model.Reminder
import com.candorfoods.factoryops.domain.model.ReminderCategory
import com.candorfoods.factoryops.domain.model.ReminderType
import com.candorfoods.factoryops.domain.model.UserRole

/**
 * FCM data-message contract for the future server-push path. The backend sends these
 * fields in the message `data` map; the client maps them to the same [Reminder] the
 * local evaluator produces, so push and poll converge on one dispatcher.
 *
 * Required keys: type, category, entityId, title, body. Optional: targetRole,
 * targetUserId, tier (default 0), deepLinkExtraKey (defaulted by category).
 */
object NotificationEnvelope {

    fun parse(data: Map<String, String>): Reminder? {
        val type = data["type"]?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() } ?: return null
        val category = data["category"]?.let { runCatching { ReminderCategory.valueOf(it) }.getOrNull() } ?: return null
        val entityId = data["entityId"]?.takeIf { it.isNotBlank() } ?: return null
        val role = data["targetRole"]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.TECHNICIAN
        val tier = data["tier"]?.toIntOrNull() ?: 0
        val title = data["title"] ?: return null
        val body = data["body"] ?: return null
        val deepLinkExtraKey = data["deepLinkExtraKey"] ?: when (category) {
            ReminderCategory.BREAKDOWN -> "ticket_id"
            ReminderCategory.TRANSFER -> "transfer_id"
            ReminderCategory.PM -> "pm_wo_id"
        }
        return Reminder(
            type = type, category = category, entityId = entityId,
            recipientRole = role, tier = tier, title = title, body = body,
            deepLinkExtraKey = deepLinkExtraKey,
        )
    }
}
