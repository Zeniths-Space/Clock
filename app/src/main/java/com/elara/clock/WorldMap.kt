package com.elara.clock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A dotted world map, as in the design: land drawn as a grid of dots. The land is a coarse hand-made mask
 * on a 5-degree grid (enough for decoration, not for geography), sampled every 2.5 degrees.
 *
 * The map is equirectangular from 180 W to 180 E and from 80 N to 60 S; [x] and [y] give a place's
 * position on it, from 0 to 1.
 */
object WorldMap {
    const val NORTH = 80f
    const val SOUTH = -60f

    fun x(lon: Double) = ((lon + 180) / 360).toFloat()
    fun y(lat: Double) = ((NORTH - lat) / (NORTH - SOUTH)).toFloat()

    /** The longitude and latitude at [x] and [y] on the map, the inverse of [x] and [y]. */
    fun lon(x: Float) = x * 360.0 - 180
    fun lat(y: Float) = NORTH - y * (NORTH - SOUTH).toDouble()

    /** Width over height of the map. */
    const val ASPECT = 360f / (NORTH - SOUTH)

    // For each latitude, the runs of land as 5-degree column ranges (column c starts at longitude -180 + 5c).
    private val land: Map<Int, List<IntRange>> = mapOf(
        75 to listOf(12..22, 22..32, 39..40, 47..58, 64..64),
        70 to listOf(3..8, 8..23, 25..32, 40..42, 42..71),
        65 to listOf(3..16, 19..23, 26..29, 32..33, 38..42, 42..71),
        60 to listOf(3..15, 19..23, 27..27, 37..42, 42..68),
        55 to listOf(10..16, 18..24, 34..35, 38..44, 44..68),
        50 to listOf(11..25, 35..37, 36..44, 44..64, 64..64),
        45 to listOf(11..23, 35..40, 42..44, 46..63, 64..65),
        40 to listOf(11..21, 34..36, 38..42, 41..45, 48..61, 64..64),
        35 to listOf(12..20, 34..38, 40..43, 43..48, 48..60, 63..63),
        30 to listOf(13..19, 34..42, 42..46, 46..50, 50..60),
        25 to listOf(14..16, 33..43, 43..47, 50..54, 56..60),
        20 to listOf(15..18, 20..20, 33..43, 44..47, 50..53, 55..58),
        15 to listOf(18..19, 33..44, 45..45, 51..52, 56..57, 60..60),
        10 to listOf(19..20, 21..23, 33..45, 46..46, 51..51, 56..56, 61..61),
        5 to listOf(20..26, 34..45, 56..59),
        0 to listOf(20..26, 38..44, 56..59),
        -5 to listOf(20..29, 38..44, 57..60, 62..66),
        -10 to listOf(21..28, 38..44, 58..59, 62..64),
        -15 to listOf(21..28, 38..44, 45..46, 61..65),
        -20 to listOf(22..28, 38..43, 45..46, 59..66),
        -25 to listOf(22..26, 39..42, 58..66),
        -30 to listOf(21..25, 39..42, 59..66),
        -35 to listOf(21..24, 40..40, 59..65, 70..70),
        -40 to listOf(21..23, 65..65, 70..70),
        -45 to listOf(21..22, 69..70),
        -50 to listOf(21..22),
        -55 to listOf(22..22),
    )

    private fun isLand(lat: Double, lon: Double): Boolean {
        val row = ((lat / 5).roundToInt()) * 5
        val col = floor((lon + 180) / 5).toInt()
        return land[row]?.any { col in it } == true
    }

    /** The dots, as positions from 0 to 1 on the map; made once. */
    val dots: List<Offset> by lazy {
        buildList {
            var lat = NORTH.toDouble()
            while (lat >= SOUTH + 2.5) {
                var lon = -180.0
                while (lon < 180) {
                    if (isLand(lat, lon)) add(Offset(x(lon), y(lat)))
                    lon += 2.5
                }
                lat -= 2.5
            }
        }
    }
}

/** The dots in px for a map with its top left at [origin] and [width] px wide. Make it in drawWithCache. */
fun worldMapPoints(origin: Offset, width: Float): List<Offset> {
    val height = width / WorldMap.ASPECT
    return WorldMap.dots.map { Offset(origin.x + it.x * width, origin.y + it.y * height) }
}

/** Draws [points] from [worldMapPoints] as round dots [dotPx] across, in one draw call. */
fun DrawScope.drawWorldMap(points: List<Offset>, color: Color, dotPx: Float) =
    drawPoints(points, PointMode.Points, color, strokeWidth = dotPx, cap = StrokeCap.Round)
