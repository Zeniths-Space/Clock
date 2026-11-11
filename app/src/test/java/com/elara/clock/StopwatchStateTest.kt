package com.elara.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchStateTest {
    @Test fun runningCountsFromItsStart() {
        val s = StopwatchState(startedAt = 1_000)
        assertTrue(s.running)
        assertEquals(4_500L, s.elapsed(now = 5_500))
    }

    @Test fun pausedHoldsItsTime() {
        val s = StopwatchState(heldMs = 61_230, laps = listOf(61_000, 30_000))
        assertFalse(s.running)
        assertFalse(s.idle)
        assertEquals(61_230L, s.elapsed(now = 999_999))
    }

    @Test fun resetIsIdle() {
        assertTrue(StopwatchState().idle)
    }
}
