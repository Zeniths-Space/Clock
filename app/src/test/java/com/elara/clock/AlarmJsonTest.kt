package com.elara.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AlarmJsonTest {
    @Test fun roundTrip() {
        val alarms = listOf(
            Alarm(1, LocalTime.of(6, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), "Gym", on = true, vibrate = false, snoozedUntil = 123L),
            Alarm(2, LocalTime.of(23, 59), label = "Tab\tand \"quotes\""),
        )
        assertEquals(alarms, Alarm.listFromJson(Alarm.listToJson(alarms)))
    }

    @Test fun badEntriesAreSkippedNotFatal() {
        val text = """[{"id":1,"time":390,"days":0},{"nonsense":true},{"id":3,"time":60}]"""
        assertEquals(listOf(1, 3), Alarm.listFromJson(text).map { it.id })
    }

    @Test fun unreadableFileGivesNoAlarms() {
        assertEquals(emptyList<Alarm>(), Alarm.listFromJson("not json"))
    }
}
