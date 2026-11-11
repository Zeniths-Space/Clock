package com.elara.clock

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * One alarm. [days] empty means it rings once, then turns itself off. [sound] off rings silently (vibration
 * and the screen only); [tone] is its own alarm sound (a content URI), or null for the default in Settings
 * ([Tones]); [snooze] off leaves only Dismiss. [snoozedUntil] is epoch millis while a snooze is
 * pending, else 0; it is stored so a snooze survives a reboot.
 */
data class Alarm(
    val id: Int,
    val time: LocalTime,
    val days: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val on: Boolean = true,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val tone: String? = null,
    val snooze: Boolean = true,
    val snoozedUntil: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("time", time.hour * 60 + time.minute)
        .put("days", days.sumOf { 1 shl (it.value - 1) })
        .put("label", label)
        .put("on", on)
        .put("vibrate", vibrate)
        .put("sound", sound)
        .put("tone", tone)
        .put("snooze", snooze)
        .put("snoozedUntil", snoozedUntil)

    companion object {
        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            time = o.getInt("time").let { LocalTime.of(it / 60, it % 60) },
            days = o.optInt("days").let { bits -> DayOfWeek.entries.filterTo(HashSet()) { bits and (1 shl (it.value - 1)) != 0 } },
            label = o.optString("label"),
            on = o.optBoolean("on", true),
            vibrate = o.optBoolean("vibrate", true),
            sound = o.optBoolean("sound", true),
            tone = o.optString("tone").ifEmpty { null },
            snooze = o.optBoolean("snooze", true),
            snoozedUntil = o.optLong("snoozedUntil"),
        )

        fun listToJson(alarms: List<Alarm>): String = JSONArray().apply { alarms.forEach { put(it.toJson()) } }.toString()

        /** The alarms in [text]; entries that can't be read are skipped, so one bad entry never loses the rest. */
        fun listFromJson(text: String): List<Alarm> = runCatching {
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }
}

/**
 * When [alarm] next rings after [now]: a pending snooze first, else its next day at its time; null when it is
 * off. Times are wall-clock times in [now]'s zone, so a daylight-saving change keeps 07:00 at 07:00 (a time
 * that does not exist that day rings at the first moment after the gap, as java.time resolves it).
 */
fun nextRing(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
    if (!alarm.on) return null
    if (alarm.snoozedUntil > now.toInstant().toEpochMilli()) return Instant.ofEpochMilli(alarm.snoozedUntil).atZone(now.zone)
    for (d in 0..7) {
        val day = now.toLocalDate().plusDays(d.toLong())
        val at = day.atTime(alarm.time).atZone(now.zone)
        if (at.isAfter(now) && (alarm.days.isEmpty() || day.dayOfWeek in alarm.days)) return at
    }
    return null
}

/** "MON WED FRI", "WEEKDAYS", "WEEKENDS", "EVERY DAY", or "ONCE". */
fun daysLabel(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> "ONCE"
    days.size == 7 -> "EVERY DAY"
    days == setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY) -> "WEEKDAYS"
    days == setOf(SATURDAY, SUNDAY) -> "WEEKENDS"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() }
}
