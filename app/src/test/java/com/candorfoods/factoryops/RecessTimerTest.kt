package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.usecase.energy.cumulativeActiveMs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecessTimerTest {

    private val min = 60_000L

    @Test
    fun `running with no prior segments counts only the current segment`() {
        val start = 1_000_000L
        val now = start + 5 * min
        assertEquals(5 * min, cumulativeActiveMs(accumulatedActiveMs = 0L, currentSegmentStartMs = start, nowMs = now))
    }

    @Test
    fun `on recess the timer is frozen at the accumulated total`() {
        // 30 min ran, then paused: no current segment, recess does not add time.
        assertEquals(30 * min, cumulativeActiveMs(accumulatedActiveMs = 30 * min, currentSegmentStartMs = null, nowMs = 9_999_999L))
    }

    @Test
    fun `after resume the running segment adds onto the accumulated total`() {
        // 30 min before recess + 10 min running after resume = 40 min active (recess excluded).
        val resumeStart = 2_000_000L
        val now = resumeStart + 10 * min
        assertEquals(40 * min, cumulativeActiveMs(accumulatedActiveMs = 30 * min, currentSegmentStartMs = resumeStart, nowMs = now))
    }

    @Test
    fun `negative clock skew never makes the current segment go below zero`() {
        val start = 5_000_000L
        val now = start - 1_000L   // clock went backwards
        assertEquals(30 * min, cumulativeActiveMs(accumulatedActiveMs = 30 * min, currentSegmentStartMs = start, nowMs = now))
    }
}
