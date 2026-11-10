package com.elara.clock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * A city on the clock: [code] is its short chip label; [cc] its ISO country code; [lat] and [lon] in degrees.
 * [major] marks a large city, labelled first on the city map.
 */
data class City(
    val name: String, val cc: String, val code: String, val zone: ZoneId, val lat: Double, val lon: Double,
    val major: Boolean = false,
) {
    val country: String get() = if (cc.isEmpty()) "" else Locale("", cc).displayCountry

    /** One tab-separated line, as in assets/cities.tsv: name, country code, zone, lat, lon, code (without [major]). */
    fun toLine() = "$name\t$cc\t${zone.id}\t$lat\t$lon\t$code"

    companion object {
        /** The city in [line], or null when it can't be read or this phone doesn't know its time zone. */
        fun fromLine(line: String): City? = runCatching {
            val f = line.split('\t')
            City(f[0], f[1], f[5], ZoneId.of(f[2]), f[3].toDouble(), f[4].toDouble(), f.getOrNull(6) == "1")
        }.getOrNull()
    }
}

/**
 * Cities: every place the app knows (assets/cities.tsv, made by tools/cities.py from the tz database), and
 * the ones you chose for the world clock, kept in preferences and read once per process.
 */
object Cities {
    private const val KEY = "cities"

    @Volatile private var catalog: List<City>? = null

    /** Every city the app knows, sorted by name. Parses a 24 KB asset the first time (a few ms). */
    fun catalog(context: Context): List<City> = catalog ?: context.assets.open("cities.tsv").bufferedReader().useLines { lines ->
        lines.mapNotNull(City::fromLine).toList()
    }.also { catalog = it }

    private var chosen by mutableStateOf<List<City>?>(null)

    /** Your cities, in the order you added them; a Compose state, so screens follow changes. */
    fun chosen(context: Context): List<City> = chosen ?: prefs(context).getString(KEY, null)
        .orEmpty().split('\n').filter(String::isNotEmpty) // None until you add some.
        .mapNotNull(City::fromLine)
        .also { chosen = it }

    fun add(context: Context, city: City) = save(context, chosen(context).filterNot { it.same(city) } + city)

    fun remove(context: Context, city: City) = save(context, chosen(context).filterNot { it.same(city) })

    private fun save(context: Context, list: List<City>) {
        chosen = list
        prefs(context).edit { putString(KEY, list.joinToString("\n") { it.toLine() }) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("world", Context.MODE_PRIVATE)

    /**
     * The phone's own city: one of [chosen] in its time zone, else the catalog's city for that zone, else one
     * named after the zone at its offset's longitude.
     */
    fun home(context: Context, zone: ZoneId = ZoneId.systemDefault()): City {
        fun City.inZone() = this.zone.normalized() == zone.normalized() || this.zone.id == zone.id
        return chosen(context).firstOrNull { it.inZone() }
            ?: catalog(context).firstOrNull { it.zone.id == zone.id }
            ?: zone.id.substringAfterLast('/').replace('_', ' ').let { name ->
                val offsetHours = zone.rules.getOffset(Instant.now()).totalSeconds / 3600.0
                City(name, "", name.take(3).uppercase(), zone, 0.0, offsetHours * 15)
            }
    }
}

/** Same place: same name in the same zone (coordinates may differ between the defaults and the catalog). */
fun City.same(other: City) = name == other.name && zone.id == other.zone.id

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

/**
 * Where the sun is overhead at [at]: latitude and longitude in degrees, from the usual low-precision solar
 * formulas (within about half a degree, plenty for shading a map).
 */
fun subsolarPoint(at: Instant): Pair<Double, Double> {
    val rad = Math.PI / 180
    val n = at.toEpochMilli() / 86_400_000.0 - 10957.5 // Days from J2000 (2000-01-01 12:00 UTC).
    val l = (280.460 + 0.9856474 * n).mod(360.0)
    val g = (357.528 + 0.9856003 * n).mod(360.0) * rad
    val lambda = (l + 1.915 * sin(g) + 0.020 * sin(2 * g)) * rad
    val e = 23.439 * rad
    val decl = asin(sin(e) * sin(lambda)) / rad
    val ra = Math.toDegrees(kotlin.math.atan2(cos(e) * sin(lambda), cos(lambda))).mod(360.0)
    val gmst = (280.46061837 + 360.98564736629 * n).mod(360.0)
    val lon = (ra - gmst + 540).mod(360.0) - 180
    return decl to lon
}

