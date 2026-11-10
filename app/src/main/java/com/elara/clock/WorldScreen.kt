package com.elara.clock

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.tan
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.abs
import kotlin.math.roundToInt

/** Your cities: where they are on the map, and what time it is there next to your own. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorldScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val home = remember { Cities.home(context) }
    val twentyFour = DateFormat.is24HourFormat(context)
    val chosen = Cities.chosen(context)
    val others = chosen.filterNot { it.same(home) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<City?>(null) }
    val homeDate = now.atZone(home.zone).toLocalDate()
    val removeLabel = stringResource(R.string.remove)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.cities_count, others.size + 1, others.size + 1), style = smallText(12), modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Plus, stringResource(R.string.add_city)) { adding = true }
        }
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.your_cities))
                append("\n")
                withStyle(SpanStyle(color = Ink.soft)) { append(stringResource(R.string.around_world)) }
            },
            style = sans(34, 400, spacing = -0.5f).copy(lineHeight = 1.08.em),
            modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
        )

        MapCard(home, others, now, twentyFour)

        if (others.isEmpty()) {
            Text(stringResource(R.string.no_cities), style = sans(14, color = Ink.muted), modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp))
        }
        others.forEach { city ->
            val there = now.atZone(city.zone)
            val diff = offsetBetween(home.zone, city.zone, now)
            val diffText = when {
                diff.isZero -> stringResource(R.string.same_time)
                diff.isNegative -> stringResource(R.string.behind, spanText(diff))
                else -> stringResource(R.string.ahead, spanText(diff))
            }
            val day = there.toLocalDate().compareTo(homeDate).let {
                when {
                    it < 0 -> stringResource(R.string.yesterday)
                    it > 0 -> stringResource(R.string.tomorrow)
                    else -> stringResource(R.string.today)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(Ink.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .combinedClickable(onLongClickLabel = removeLabel, onLongClick = { removing = city }, onClick = {})
                    .padding(horizontal = 4.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(utcLabel(city.zone, now), style = smallText(11))
                    Text(city.name, style = sans(17, 500), modifier = Modifier.padding(vertical = 3.dp))
                    Text("$day · $diffText", style = sans(12, color = Ink.muted))
                }
                Text(there.hm(twentyFour), style = sans(44, 300, spacing = -1.5f))
                if (!twentyFour) Text(if (there.hour < 12) "am" else "pm", style = sans(13, 500, Ink.muted), modifier = Modifier.padding(start = 4.dp, top = 14.dp))
            }
        }
    }
    if (adding) CitySheet(onClose = { adding = false })
    removing?.let { city ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = if (Ink.dark) Color(0xFF1F1F1F) else Color.White,
            title = { Text(stringResource(R.string.remove_city, city.name), style = sans(20, 500)) },
            text = { Text(stringResource(R.string.remove_city_text), style = sans(15, color = Ink.muted)) },
            confirmButton = {
                TextButton(onClick = { Cities.remove(context, city); removing = null }) {
                    Text(removeLabel, style = sans(15, 500, if (Ink.dark) Color(0xFFFF8A8A) else Color(0xFFD93A3A)))
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel), style = sans(15, 500, Ink.accent)) } },
        )
    }
}

private val BubbleWidth = 46.dp
private val BubbleHeight = 20.dp
private val Pin = 14.dp

/**
 * The night-blue card: the dotted map with the night side shaded where the sun is down now, every city as a
 * dot, and its time in a bubble beside it. Each bubble goes above its dot, else below, else shifted left or
 * right, wherever it overlaps no other bubble or dot; a city with no free place keeps its dot only.
 */
@Composable
private fun MapCard(home: City, others: List<City>, now: java.time.Instant, twentyFour: Boolean) {
    val shape = RoundedCornerShape(30.dp)
    // The map overhangs the card by 15 dp each side, so the land runs off its edges, as in the design.
    val mapLeft = (-15).dp
    val mapTop = 36.dp
    val cities = listOf(home) + others
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(214.dp)
            .shadow(18.dp, shape, ambientColor = Ink.text.copy(alpha = 0.3f), spotColor = Ink.text.copy(alpha = 0.3f))
            .clip(shape)
            .drawWithCache {
                val sky = Brush.linearGradient(listOf(Ink.night, Ink.nightDeep), Offset(0f, 0f), Offset(size.width * 0.3f, size.height))
                val glow = Brush.radialGradient(listOf(Color(0xFF4A2BB0), Color(0x004A2BB0)), Offset(size.width * 0.7f, 0f), size.width * 0.8f)
                val origin = Offset(mapLeft.toPx(), mapTop.toPx())
                val w = size.width - 2 * mapLeft.toPx()
                val h = w / WorldMap.ASPECT
                val sun = subsolarPoint(now)
                val (day, night) = WorldMap.dots.partition { isDay(WorldMap.lat(it.y), WorldMap.lon(it.x), sun) }
                fun List<Offset>.onCard() = map { Offset(origin.x + it.x * w, origin.y + it.y * h) }
                val dayPoints = day.onCard()
                val nightPoints = night.onCard()
                val shade = nightPath(sun, origin, w, h, size)
                val dot = 1.7.dp.toPx()
                onDrawBehind {
                    drawRect(sky)
                    drawRect(glow)
                    drawPath(shade, Color.Black.copy(alpha = 0.32f))
                    drawWorldMap(nightPoints, Ink.lavender.copy(alpha = 0.22f), dot)
                    drawWorldMap(dayPoints, Ink.lavender.copy(alpha = 0.6f), dot)
                }
            }
            .clearAndSetSemantics {},
    ) {
        val cardWidth = maxWidth
        val mapWidth = cardWidth - mapLeft * 2
        val mapHeight = mapWidth / WorldMap.ASPECT
        val spots = cities.map { DpOffset(mapLeft + mapWidth * WorldMap.x(it.lon), mapTop + mapHeight * WorldMap.y(it.lat)) }
        val bubbles = remember(cities, cardWidth) { placeBubbles(spots, cardWidth) }
        // Others first, so your city's dot is drawn on top.
        cities.indices.reversed().forEach { i ->
            val isHome = i == 0
            Box(
                Modifier
                    .pinAt(spots[i].x, spots[i].y)
                    .size(Pin)
                    .background((if (isHome) Ink.lilac else Color.White).copy(alpha = if (isHome) 0.35f else 0.18f), CircleShape)
                    .padding(3.5.dp)
                    .background(if (isHome) Ink.lilac else Color.White, CircleShape),
            )
        }
        cities.forEachIndexed { i, city ->
            val at = bubbles[i] ?: return@forEachIndexed
            val isHome = i == 0
            Text(
                now.atZone(city.zone).hm(twentyFour),
                style = smallText(10, if (isHome) Color.White else Ink.night, weight = 500),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .pinAt(at.x, at.y)
                    .size(BubbleWidth, BubbleHeight)
                    .background(if (isHome) Ink.purple else Ink.lavender, RoundedCornerShape(9.dp))
                    .wrapContentHeight(),
            )
        }
        Text(
            stringResource(R.string.map_now, now.atZone(home.zone).hm(twentyFour), home.name),
            style = sans(12, color = Ink.lavender),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp),
        )
    }
}

/**
 * Where each city's time bubble goes (its centre), in the order of [spots], or null when it fits nowhere.
 * Tries above the dot, below it, then each shifted half a bubble left or right; a place must stay inside the
 * card and clear of every bubble already placed and every dot.
 */
internal fun placeBubbles(spots: List<DpOffset>, cardWidth: Dp): List<DpOffset?> {
    val gap = Pin / 2 + 4.dp + BubbleHeight / 2
    val shift = BubbleWidth * 0.6f
    val placed = mutableListOf<DpOffset>()
    fun clear(c: DpOffset) =
        c.x - BubbleWidth / 2 >= 6.dp && c.x + BubbleWidth / 2 <= cardWidth - 6.dp && c.y - BubbleHeight / 2 >= 4.dp &&
            placed.none { abs((it.x - c.x).value) < BubbleWidth.value + 2 && abs((it.y - c.y).value) < BubbleHeight.value + 2 } &&
            spots.none { abs((it.x - c.x).value) < (BubbleWidth + Pin).value / 2 && abs((it.y - c.y).value) < (BubbleHeight + Pin).value / 2 }
    return spots.map { s ->
        listOf(0.dp, -shift, shift).flatMap { dx -> listOf(DpOffset(s.x + dx, s.y - gap), DpOffset(s.x + dx, s.y + gap)) }
            .firstOrNull(::clear)
            ?.also { placed += it }
    }
}

/**
 * The night side as a shape on the card: for each longitude, from the line where the sun is on the horizon
 * to the card's bottom edge (northern summer) or top edge (northern winter).
 */
private fun nightPath(sun: Pair<Double, Double>, origin: Offset, w: Float, h: Float, card: androidx.compose.ui.geometry.Size): Path {
    val rad = Math.PI / 180
    // Near an equinox the terminator runs nearly pole to pole; keep tan(decl) away from 0.
    val decl = sun.first.let { if (abs(it) < 0.2) 0.2 * (if (it < 0) -1 else 1) else it }
    val edge = if (decl > 0) card.height else 0f
    val step = w / 180 // Two degrees of longitude.
    return Path().apply {
        moveTo(0f, edge)
        var x = 0f
        while (true) {
            val lon = WorldMap.lon((x - origin.x) / w)
            val lat = atan(-cos((lon - sun.second) * rad) / tan(decl * rad)) / rad
            lineTo(x, origin.y + WorldMap.y(lat) * h)
            if (x >= card.width) break
            x = minOf(x + step, card.width)
        }
        lineTo(card.width, edge)
        close()
    }
}

/** Places this element centred on ([x], [y]). */
private fun Modifier.pinAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place((x.toPx() - p.width / 2f).roundToInt(), (y.toPx() - p.height / 2f).roundToInt()) }
}
