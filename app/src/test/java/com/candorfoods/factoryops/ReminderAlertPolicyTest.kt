package com.candorfoods.factoryops

import com.candorfoods.factoryops.notification.ReminderAction
import com.candorfoods.factoryops.notification.reminderAction
import com.candorfoods.factoryops.notification.shouldAlert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderAlertPolicyTest {

    private val cooldown = 180L * 60_000L   // 3 hours
    private val now = 10_000_000_000L

    @Test fun `brand new reminder alerts`() {
        // Not in shade (priorTier -1), never alerted (lastAlertAt 0).
        assertTrue(shouldAlert(priorTier = -1, tier = 0, lastAlertAtMs = 0L, nowMs = now, cooldownMs = cooldown))
    }

    @Test fun `still visible at same tier refreshes silently`() {
        assertFalse(shouldAlert(priorTier = 0, tier = 0, lastAlertAtMs = now, nowMs = now, cooldownMs = cooldown))
    }

    @Test fun `tier escalation while visible always alerts`() {
        // Even though we just alerted moments ago, a higher tier breaks through.
        assertTrue(shouldAlert(priorTier = 0, tier = 1, lastAlertAtMs = now - 60_000L, nowMs = now, cooldownMs = cooldown))
    }

    @Test fun `cleared and re-polled within cooldown stays silent`() {
        // User swiped it away; 2 minutes later the poll sees it again.
        val twoMinAgo = now - 2 * 60_000L
        assertFalse(shouldAlert(priorTier = -1, tier = 0, lastAlertAtMs = twoMinAgo, nowMs = now, cooldownMs = cooldown))
    }

    @Test fun `cleared and re-polled after cooldown alerts again`() {
        val threeHoursOneMinAgo = now - (181L * 60_000L)
        assertTrue(shouldAlert(priorTier = -1, tier = 0, lastAlertAtMs = threeHoursOneMinAgo, nowMs = now, cooldownMs = cooldown))
    }

    // ─── three-way action (the actual fix for the every-2-min buzz) ──────────────

    @Test fun `swiped away within cooldown is skipped, not re-posted`() {
        // priorTier -1 (not in shade), last buzzed 2 min ago → do NOT re-post (would buzz).
        val twoMinAgo = now - 2 * 60_000L
        assertEquals(
            ReminderAction.SKIP,
            reminderAction(showing = false, priorTier = -1, tier = 0, lastAlertAtMs = twoMinAgo, nowMs = now, cooldownMs = cooldown),
        )
    }

    @Test fun `still in shade at same tier updates silently`() {
        assertEquals(
            ReminderAction.SILENT_UPDATE,
            reminderAction(showing = true, priorTier = 0, tier = 0, lastAlertAtMs = now, nowMs = now, cooldownMs = cooldown),
        )
    }

    @Test fun `new reminder alerts`() {
        assertEquals(
            ReminderAction.ALERT,
            reminderAction(showing = false, priorTier = -1, tier = 0, lastAlertAtMs = 0L, nowMs = now, cooldownMs = cooldown),
        )
    }

    @Test fun `escalation while in shade alerts`() {
        assertEquals(
            ReminderAction.ALERT,
            reminderAction(showing = true, priorTier = 0, tier = 1, lastAlertAtMs = now - 60_000L, nowMs = now, cooldownMs = cooldown),
        )
    }

    @Test fun `swiped away after cooldown re-alerts`() {
        val afterCooldown = now - (181L * 60_000L)
        assertEquals(
            ReminderAction.ALERT,
            reminderAction(showing = false, priorTier = -1, tier = 0, lastAlertAtMs = afterCooldown, nowMs = now, cooldownMs = cooldown),
        )
    }
}
