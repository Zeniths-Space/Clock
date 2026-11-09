package com.elara.clock

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** A city on the clock: [code] is its short chip label; [lat] and [lon] in degrees. */
data class City(val name: String, val country: String, val code: String, val zone: ZoneId, val lat: Double, val lon: Double)

/** The cities this first version knows: shown as favourites until cities can be added. */
object Cities {
    val Kathmandu = City("Kathmandu", "Nepal", "KTM", ZoneId.of("Asia/Kathmandu"), 27.7172, 85.3240)
    val Delhi = City("Delhi", "India", "DEL", ZoneId.of("Asia/Kolkata"), 28.6139, 77.2090)
    val Dubai = City("Dubai", "UAE", "DXB", ZoneId.of("Asia/Dubai"), 25.2048, 55.2708)
    val London = City("London", "UK", "LON", ZoneId.of("Europe/London"), 51.5072, -0.1276)
    val NewYork = City("New York", "USA", "NYC", ZoneId.of("America/New_York"), 40.7128, -74.0060)
    val Tokyo = City("Tokyo", "Japan", "TYO", ZoneId.of("Asia/Tokyo"), 35.6762, 139.6503)
    val all = listOf(Kathmandu, Delhi, Dubai, London, NewYork, Tokyo)

    /** The phone's own city when it is one of [all]; otherwise one named after the time zone, at its offset's longitude. */
    fun home(zone: ZoneId = ZoneId.systemDefault()): City =
        all.firstOrNull { it.zone.normalized() == zone.normalized() || it.zone.id == zone.id }
            ?: zone.id.substringAfterLast('/').replace('_', ' ').let { name ->
                val offsetHours = zone.rules.getOffset(Instant.now()).totalSeconds / 3600.0
                City(name, "", name.take(3).uppercase(), zone, 0.0, offsetHours * 15)
            }
}

/** "UTC +5:45", "UTC −4", "UTC +0". */
fun utcLabel(zone: ZoneId, at: Instant): String {
    val s = zone.rules.getOffset(at).totalSeconds
    val sign = if (s < 0) "−" else "+"
    val h = abs(s) / 3600
    val m = abs(s) % 3600 / 60
    return if (m == 0) "UTC $sign$h" else "UTC $sign$h:${m.toString().padStart(2, '0')}"
}

/** How far [other]'s clock is from [home]'s: positive when it is ahead. */
fun offsetBetween(home: ZoneId, other: ZoneId, at: Instant): Duration =
    Duration.ofSeconds((other.rules.getOffset(at).totalSeconds - home.rules.getOffset(at).totalSeconds).toLong())

/** "5 h 45 min", "3 h", "45 min" for a positive or negative [d]; a day or more is "6 d 7 h". */
fun spanText(d: Duration): String {
    val total = abs(d.toMinutes())
    val h = total / 60
    val m = total % 60
    if (h >= 24) return if (h % 24 == 0L) "${h / 24} d" else "${h / 24} d ${h % 24} h"
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val HourMinute12: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm")

fun ZonedDateTime.hm(twentyFour: Boolean): String = format(if (twentyFour) HourMinute else HourMinute12)

/**
 * Sunrise and sunset at [lat], [lon] on [date], in [zone], with the standard sunrise equation (about a
 * minute out at mid latitudes). Null in polar day or night.
 */
fun sunTimes(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): Pair<LocalTime, LocalTime>? {
    val rad = Math.PI / 180
    val n = date.toEpochDay() - 10957.0 // Days from 2000-01-01 (J2000), at noon.
    val jStar = n - lon / 360
    val m = (357.5291 + 0.98560028 * jStar).mod(360.0)
    val c = 1.9148 * sin(m * rad) + 0.02 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
    val lambda = (m + c + 180 + 102.9372).mod(360.0)
    val transit = 2451545.0 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lambda * rad)
    val decl = asin(sin(lambda * rad) * sin(23.4397 * rad))
    val cosW = (sin(-0.833 * rad) - sin(lat * rad) * sin(decl)) / (cos(lat * rad) * cos(decl))
    if (cosW < -1 || cosW > 1) return null
    val w = acos(cosW) / rad
    fun local(jd: Double) = Instant.ofEpochMilli(((jd - 2440587.5) * 86_400_000).toLong()).atZone(zone).toLocalTime()
    return local(transit - w / 360) to local(transit + w / 360)
}
