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
        val alarm = Alarm(1, LocalTime.of(6, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), "Gym")
        // Monday 2026-11-09 at 20:00: Monday's has passed, so Wednesday's.
        val now = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, ktm)
        assertEquals(ZonedDateTime.of(2026, 11, 11, 6, 30, 0, 0, ktm), nextRing(alarm, now))
    }

    @Test fun oneTimeAlarmOnADate() {
        val now = ZonedDateTime.of(2026, 11, 11, 21, 0, 0, 0, ktm)
        val alarm = Alarm(1, LocalTime.of(7, 0), date = LocalDate.of(2026, 11, 18))
        assertEquals(ZonedDateTime.of(2026, 11, 18, 7, 0, 0, 0, ktm), nextRing(alarm, now))
        // Passed: nothing left to ring.
        assertNull(nextRing(alarm.copy(date = LocalDate.of(2026, 11, 11)), now))
        // Days win over a date.
        assertEquals(
            ZonedDateTime.of(2026, 11, 12, 7, 0, 0, 0, ktm),
            nextRing(alarm.copy(days = setOf(java.time.DayOfWeek.THURSDAY)), now),
        )
        assertEquals("WED 18 NOV", daysLabel(alarm))
    }

    @Test fun nextRingLaterToday() {
        val alarm = Alarm(2, LocalTime.of(21, 0), DayOfWeek.entries.toSet(), "Medicine")
        val now = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, ktm)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 21, 0, 0, 0, ktm), nextRing(alarm, now))
    }

    @Test fun offAlarmNeverRings() {
        assertNull(nextRing(Alarm(3, LocalTime.NOON, on = false), ZonedDateTime.now(ktm)))
    }

    @Test fun oneTimeAlarmRingsTomorrowWhenTodaysTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, ktm)
        assertEquals(ZonedDateTime.of(2026, 11, 10, 6, 0, 0, 0, ktm), nextRing(Alarm(4, LocalTime.of(6, 0)), now))
    }

    @Test fun pendingSnoozeComesFirst() {
        val now = ZonedDateTime.of(2026, 11, 9, 6, 30, 0, 0, ktm)
        val until = now.plusMinutes(10)
        val alarm = Alarm(5, LocalTime.of(6, 30), DayOfWeek.entries.toSet(), snoozedUntil = until.toInstant().toEpochMilli())
        assertEquals(until.toInstant(), nextRing(alarm, now)!!.toInstant())
    }

    @Test fun nextAlarmIsTheSoonestOn() {
        val now = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, ktm)
        val list = listOf(Alarm(1, LocalTime.of(6, 30)), Alarm(2, LocalTime.of(21, 0)), Alarm(3, LocalTime.of(20, 30), on = false))
        assertEquals(2, AlarmEngine.next(list, now)!!.first.id)
    }

    @Test fun daylightSavingKeepsWallClockTime() {
        // London springs forward on 2026-03-29: 07:00 is still 07:00 local, one hour less after midnight.
        val london = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, london)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 7, 0, 0, 0, london), nextRing(Alarm(6, LocalTime.of(7, 0)), now))
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
        assertEquals("6 d 7 h", spanText(Duration.ofHours(151).plusMinutes(41)))
        assertEquals("2 d", spanText(Duration.ofHours(48)))
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
