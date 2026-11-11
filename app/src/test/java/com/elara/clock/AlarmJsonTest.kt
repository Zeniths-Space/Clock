package com.elara.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AlarmJsonTest {
    @Test fun roundTrip() {
        val alarms = listOf(
            Alarm(1, LocalTime.of(6, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), "Gym", on = true, vibrate = false, tone = "content://media/internal/audio/media/42", snoozedUntil = 123L),
            Alarm(2, LocalTime.of(23, 59), label = "Tab\tand \"quotes\"", sound = false, snooze = false),
            Alarm(3, LocalTime.of(7, 0), date = java.time.LocalDate.of(2026, 12, 31)),
        )
        assertEquals(alarms, Alarm.listFromJson(Alarm.listToJson(alarms)))
    }

    @Test fun badEntriesAreSkippedNotFatal() {
        val text = """[{"id":1,"time":390,"days":0},{"nonsense":true},{"id":3,"time":60}]"""
        assertEquals(listOf(1, 3), Alarm.listFromJson(text).map { it.id })
    }

    @Test fun olderFilesGetSoundAndSnoozeOn() {
        val a = Alarm.listFromJson("""[{"id":1,"time":390,"days":0}]""").single()
        assert(a.sound && a.snooze && a.vibrate && a.on)
        assertEquals(null, a.tone) // No sound of its own: the default in Settings.
    }

    @Test fun unreadableFileGivesNoAlarms() {
        assertEquals(emptyList<Alarm>(), Alarm.listFromJson("not json"))
    }
}
