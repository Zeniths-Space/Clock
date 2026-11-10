package com.elara.clock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons on a 24-unit grid, drawn as strokes like the design; [androidx.compose.material3.Icon] tints them. */
object Icons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun line(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    PathParser().parsePathString(it).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
        }.build()

    val Clock = line("clock", 1.8f, circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Globe = line("globe", 1.8f, circle(12f, 12f, 9f), "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18")
    val Alarm = line("alarm", 1.8f, circle(12f, 13f, 8f), "M12 9v4l2 2M5 3 2 6M22 6l-3-3")
    val Hourglass = line("hourglass", 1.8f, "M6 2h12M6 22h12M7 2v4l5 6-5 6v4M17 2v4l-5 6 5 6v4")
    val Stopwatch = line("stopwatch", 1.8f, circle(12f, 14f, 8f), "M12 10v4M10 2h4M19 7l1.5-1.5")
    val Search = line("search", 2f, circle(11f, 11f, 7f), "m20 20-3.5-3.5")
    val Sliders = line("sliders", 2f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Plus = line("plus", 2.2f, "M12 5v14M5 12h14")
    val List = line("list", 2f, "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
    val Reset = line("reset", 2f, "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5")
    val Chevron = line("chevron", 2f, "m6 9 6 6 6-6")
    val Calendar = line("calendar", 2f, "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM4 10h16M9 3v4M15 3v4")
    val Sun = line("sun", 2f, circle(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Back = line("back", 2f, "M15 5l-7 7 7 7")
    val Trash = line("trash", 2f, "M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3")
    /** A clock beside a list: the time zones page. */
    val Zones = line("zones", 2f, circle(7.5f, 12f, 5f), "M7.5 9.5V12l1.5 1M15 7h6M15 12h6M15 17h6")
    val Check = line("check", 2.2f, "m5 12.5 4.5 4.5L19 7.5")
    val Close = line("close", 2f, "M6 6l12 12M18 6 6 18")
    val Flag = line("flag", 2f, "M5 21V4M5 4h11l-2 4 2 4H5")
    val Pause = filled("pause", "M7 5h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM14 5h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z")
    val Play = filled("play", "M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z")
}
