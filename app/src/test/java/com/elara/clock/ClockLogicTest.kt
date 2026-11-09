package com.elara.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class ClockLogicTest {
    private val ktm = ZoneId.of("Asia/Kathmandu")

    private fun near(expected: LocalTime, actual: LocalTime, minutes: Long = 3) =
        assertTrue("$actual not within $minutes min of $expected", abs(Duration.between(expected, actual).toMinutes()) <= minutes)

    @Test fun sunriseAndSunsetInKathmandu() {
        // Kathmandu on 2026-11-09: sunrise about 06:19, sunset about 17:15.
        val (rise, set) = sunTimes(LocalDate.of(2026, 11, 9), 27.7172, 85.3240, ktm)!!
        near(LocalTime.of(6, 19), rise)
        near(LocalTime.of(17, 15), set)
    }

    @Test fun sunriseInLondonInJune() {
        val (rise, set) = sunTimes(LocalDate.of(2026, 6, 21), 51.5072, -0.1276, ZoneId.of("Europe/London"))!!
        near(LocalTime.of(4, 43), rise)
        near(LocalTime.of(21, 21), set)
    }

    @Test fun noSunriseInPolarNight() {
        assertNull(sunTimes(LocalDate.of(2026, 12, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")))
    }

    @Test fun nextRingSkipsDaysNotChosen() {
        val alarm = Alarm("Gym", LocalTime.of(6, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), true)
        // Monday 2026-11-09 at 20:00: Monday's has passed, so Wednesday's.
        val now = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, ktm)
        assertEquals(ZonedDateTime.of(2026, 11, 11, 6, 30, 0, 0, ktm), nextRing(alarm, now))
    }

    @Test fun nextRingLaterToday() {
        val alarm = Alarm("Medicine", LocalTime.of(21, 0), DayOfWeek.entries.toSet(), true)
        val now = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, ktm)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 21, 0, 0, 0, ktm), nextRing(alarm, now))
    }

    @Test fun offAlarmNeverRings() {
        assertNull(nextRing(Alarm("Nap", LocalTime.NOON, emptySet(), false), ZonedDateTime.now(ktm)))
    }

    @Test fun dayLabels() {
        assertEquals("EVERY DAY", daysLabel(DayOfWeek.entries.toSet()))
        assertEquals("WEEKDAYS", daysLabel(DayOfWeek.entries.take(5).toSet()))
        assertEquals("MON WED FRI", daysLabel(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        assertEquals("ONCE", daysLabel(emptySet()))
    }

    @Test fun utcLabelsWithMinutes() {
        val at = Instant.parse("2026-11-09T12:00:00Z")
        assertEquals("UTC +5:45", utcLabel(ktm, at))
        assertEquals("UTC +0", utcLabel(ZoneId.of("Europe/London"), at))
        assertEquals("UTC −5", utcLabel(ZoneId.of("America/New_York"), at))
    }

    @Test fun offsetsAndSpans() {
        val at = Instant.parse("2026-11-09T12:00:00Z")
        assertEquals("5 h 45 min", spanText(offsetBetween(ktm, ZoneId.of("Europe/London"), at)))
        assertTrue(offsetBetween(ktm, ZoneId.of("Europe/London"), at).isNegative)
        assertEquals("15 min", spanText(offsetBetween(ktm, ZoneId.of("Asia/Kolkata"), at)))
    }

    @Test fun bsDate() {
        // 2026-11-09 is 23 Kartik 2083.
        assertEquals(BsDate(2083, 7, 23), Bs.fromAd(LocalDate.of(2026, 11, 9)))
    }

    @Test fun clockAndLapText() {
        assertEquals("00:10:00", clockText(600))
        assertEquals("01:04.37", lapText(64_370))
    }
}
