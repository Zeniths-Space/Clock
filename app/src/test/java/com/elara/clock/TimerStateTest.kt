package com.elara.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerStateTest {
    @Test
    fun pausedTimerShowsWhatIsLeft() {
        val s = TimerState(leftMs = 42_000, paused = true)
        assertFalse(s.running)
        assertEquals(42_000, s.left(now = 999_999))
    }

    @Test
    fun runningTimerCountsToZeroAndNoFurther() {
        val s = TimerState(endsAt = 100_000)
        assertTrue(s.running)
        assertEquals(30_000, s.left(now = 70_000))
        assertEquals(0, s.left(now = 120_000))
    }
}
