package com.candorfoods.factoryops.domain.usecase.energy

/**
 * Cumulative ACTIVE (running) time of a production session, excluding recess gaps.
 *
 * @param accumulatedActiveMs active time from already-finished segments this session.
 * @param currentSegmentStartMs start of the segment running right now, or null when on
 *        recess / idle (then the timer is frozen at the accumulated total).
 * @param nowMs current wall-clock time.
 */
fun cumulativeActiveMs(
    accumulatedActiveMs: Long,
    currentSegmentStartMs: Long?,
    nowMs: Long,
): Long {
    val current = if (currentSegmentStartMs != null) (nowMs - currentSegmentStartMs).coerceAtLeast(0) else 0L
    return accumulatedActiveMs + current
}
