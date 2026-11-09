package com.elara.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * The design's colours. Light: the approved lavender glass on a pale ground. Dark: AMOLED, as every Elara
 * app (page black, cards #141414, raised #1F1F1F, white text, #B69CFF for purple text). [dark] follows the
 * system theme (set by [ElaraTheme]); it is snapshot state, so everything that reads a colour redraws when
 * it changes. The brand purples are the same in both.
 */
object Ink {
    var dark by mutableStateOf(false)

    /** Text, hairline marks and the dial's needle. */
    val text get() = if (dark) Color.White else Color(0xFF1B1530)
    val muted get() = if (dark) Color(0xFF9A9A9F) else Color(0xFF6E6880)
    /** The second line of a two-tone title. */
    val soft get() = if (dark) Color(0xFF6B6B72) else Color(0xFF8E87A3)
    val ground get() = if (dark) Color.Black else Color(0xFFF4F1F9)
    val line get() = if (dark) Color(0x1AFFFFFF) else Color(0x1A1B1530)
    /** Purple text and icons on the page: deep purple on light, light purple on black. */
    val accent get() = if (dark) Color(0xFFB69CFF) else purpleDeep
    /** Small chips and icon circles, and their text. */
    val chipBg get() = if (dark) purple.copy(alpha = 0.22f) else lavender
    val chipFg get() = if (dark) Color(0xFFB69CFF) else night
    /** The open tab and the chosen half of a switch, with white on it. */
    val chosen get() = if (dark) purple else Color(0xFF1B1530)
    /** The faintest of the stacked digit rows (hours on the timer, minutes on the stopwatch). */
    val faded get() = if (dark) Color(0xFF3A2A73) else wash1
    /** The dotted map on the page. */
    val mapDot get() = if (dark) lavender.copy(alpha = 0.22f) else night.copy(alpha = 0.28f)

    val wash1 = Color(0xFFD9CCFF)
    val wash2 = Color(0xFFEBDDFB)
    val purple = Color(0xFF7C4DFF)
    val purpleLight = Color(0xFF9772FF)
    val purpleDeep = Color(0xFF5527D9)
    val lavender = Color(0xFFE6DCFF)
    val lilac = Color(0xFFB9A2FF)
    val night = Color(0xFF2B1766)
    val nightDeep = Color(0xFF160C38)
}

/** The brand purple ramp, top left to bottom right: hero tiles and the main buttons. */
val PurpleRamp = Brush.linearGradient(listOf(Ink.purpleLight, Ink.purple, Ink.purpleDeep))

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(R.font.outfit, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: Int) = Font(R.font.jetbrains_mono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Geometric sans for everything; the big clock digits use its thin weights. */
val Outfit = FontFamily(outfit(200), outfit(300), outfit(400), outfit(500), outfit(600), outfit(700))

/** Small technical labels: UTC offsets, units, chips. */
val Mono = FontFamily(mono(400), mono(500))

/** Outfit at [size] sp; [spacing] is letter spacing in sp (negative to tighten big digits). */
fun outfit(size: Int, weight: Int = 400, color: Color = Ink.text, spacing: Float = 0f) =
    TextStyle(fontFamily = Outfit, fontSize = size.sp, fontWeight = FontWeight(weight), color = color, letterSpacing = spacing.sp)

fun mono(size: Int, color: Color = Ink.muted, weight: Int = 400) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = FontWeight(weight), color = color)

/**
 * Frosted glass: white at [alpha] with a bright inner edge and a soft purple shadow. A real backdrop blur
 * would cost a full-screen render pass every frame; on this pale ground the tint reads the same.
 */
fun Modifier.glass(shape: Shape, alpha: Float = 0.6f, elevation: Dp = 10.dp): Modifier =
    if (Ink.dark) {
        // AMOLED: a dark card with a raised edge; a shadow would not show on black. Fainter cards (off alarms) stay darker.
        background(Color(0xFF141414).copy(alpha = (alpha / 0.6f).coerceAtMost(1f)), shape).border(1.dp, Color(0xFF1F1F1F), shape)
    } else {
        this
            .shadow(elevation, shape, ambientColor = Ink.night.copy(alpha = 0.10f), spotColor = Ink.night.copy(alpha = 0.16f))
            .background(Color.White.copy(alpha = alpha), shape)
            .border(1.dp, Color.White.copy(alpha = 0.9f), shape)
    }

/** The page: pale ground with a lavender wash from the top right and a pinker one from the bottom left; plain black in dark mode. */
fun Modifier.pageBackground(): Modifier = drawBehind {
    drawRect(Ink.ground)
    if (Ink.dark) return@drawBehind
    val big = max(size.width, size.height)
    drawRect(Brush.radialGradient(listOf(Ink.wash1, Ink.wash1.copy(alpha = 0f)), Offset(size.width, 0f), big * 0.7f))
    drawRect(Brush.radialGradient(listOf(Ink.wash2, Ink.wash2.copy(alpha = 0f)), Offset(0f, size.height), big * 0.55f))
}

/**
 * Big digits in a row [height] tall, centred in it. The font's line box is much taller than its digits, so
 * the text is measured unbounded and drawn past the row instead of being clipped by it.
 */
@Composable
fun Digits(text: String, style: TextStyle, height: Dp, modifier: Modifier = Modifier) {
    Text(text, style = style, maxLines = 1, softWrap = false, modifier = modifier.height(height).wrapContentHeight(Alignment.CenterVertically, unbounded = true))
}

@Composable
fun ElaraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    Ink.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB69CFF), background = Color.Black, surface = Color.Black, onSurface = Color.White)
        else lightColorScheme(primary = Ink.purple, background = Ink.ground, surface = Ink.ground, onSurface = Ink.text),
        content = content,
    )
}
