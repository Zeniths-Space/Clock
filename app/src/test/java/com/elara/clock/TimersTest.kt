package com.elara.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class TimersTest {
    private val running = TimerState(id = 1, endsAt = 50_000)
    private val paused = TimerState(id = 2, leftMs = 9_000, paused = true)
    private val setUp = TimerState(id = 3)

    @Test fun addKeepsOnlyActiveTimersAndShowsTheNewOne() {
        val t = Timers(listOf(running, paused, setUp), selected = 3).add(5)
        assertEquals(listOf(1, 2, 4), t.list.map { it.id })
        assertEquals(4, t.selected)
        assertEquals(5 * 60_000L, t.current.durationMs)
    }

    @Test fun pickingAnotherDropsOneOnlyBeingSetUp() {
        val t = Timers(listOf(running, setUp), selected = 3).copy(selected = 1).prune()
        assertEquals(listOf(1), t.list.map { it.id })
    }

    @Test fun neverEmpty() {
        val t = Timers(listOf(setUp), selected = 3).prune()
        assertEquals(listOf(3), t.list.map { it.id })
    }

    @Test fun namesByPlace() {
        assertEquals("Timer", Timers(listOf(running)).name("Timer", 1))
        assertEquals("Timer 2", Timers(listOf(running, paused)).name("Timer", 2))
    }
}
