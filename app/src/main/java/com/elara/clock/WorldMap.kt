package com.elara.clock

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

/** Solid land: opaque, so the fine map's overlapping tiles show no seams. */
val LandColor = Color(0xFF4A3A9C)

/**
 * The land for the World page's map and the city map: assets/land.bin (made by tools/land.py from Natural
 * Earth), in map degrees: x = lon + 180, y = 80 - lat. [coarse] is the whole world in one path; [tiles] the
 * detailed land cut into 10-degree squares, each with its bounds, so a zoomed-in map draws only what is on
 * screen. Read once, off the main thread.
 */
class Land(val coarse: Path, val tiles: List<Pair<Rect, Path>>) {
    companion object {
        @Volatile private var land: Land? = null

        /** The land if it has been read already, so a screen shown again draws it at once. */
        fun loaded(): Land? = land

        fun load(context: Context): Land = land ?: context.assets.open("land.bin").use { it.readBytes() }.let { bytes ->
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun Path.ring() {
                val n = b.int
                for (i in 0 until n) {
                    val x = b.short / 100f + 180
                    val y = WorldMap.NORTH - b.short / 100f
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val coarse = Path().apply { fillType = PathFillType.EvenOdd; repeat(b.int) { ring() } }
            val tiles = HashMap<Int, Path>()
            repeat(b.int) {
                val col = b.short.toInt()
                val row = b.short.toInt()
                tiles.getOrPut(row * 36 + col) { Path().apply { fillType = PathFillType.EvenOdd } }.ring()
            }
            Land(coarse, tiles.map { (key, path) ->
                val x = key % 36 * 10f
                val y = key / 36 * 10f - (90 - WorldMap.NORTH) // Rows count from 90 N; the map starts at 80 N.
                Rect(x, y, x + 10, y + 10) to path
            })
        }.also { land = it }
    }
}

/**
 * The night side for [sun] (from [subsolarPoint]) in map degrees, reaching well past the map's edges: for
 * each longitude, from where the sun is on the horizon to the south (northern summer) or north edge.
 */
fun nightShade(sun: Pair<Double, Double>): Path {
    val rad = Math.PI / 180
    // Near an equinox the terminator runs nearly pole to pole; keep tan(decl) away from 0.
    val decl = sun.first.let { if (kotlin.math.abs(it) < 0.2) if (it < 0) -0.2 else 0.2 else it }
    val edge = if (decl > 0) 400f else -260f
    return Path().apply {
        moveTo(-200f, edge)
        var lon = -380.0
        while (lon <= 380.0) {
            val lat = kotlin.math.atan(-kotlin.math.cos((lon - sun.second) * rad) / kotlin.math.tan(decl * rad)) / rad
            lineTo((lon + 180).toFloat(), (WorldMap.NORTH - lat).toFloat())
            lon += 2.0
        }
        lineTo(560f, edge)
        close()
    }
}

val NightColor = Color(0xFF05021A).copy(alpha = 0.42f)
