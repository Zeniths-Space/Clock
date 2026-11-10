package com.elara.clock

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
@Composable
fun WorldScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val home = remember { Cities.home(context) }
    val twentyFour = DateFormat.is24HourFormat(context)
    val chosen = Cities.chosen(context)
    val others = chosen.filterNot { it.same(home) }
    // The city map: open with a city to show it, or with your own city to add one.
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    var mapFocus by remember { mutableStateOf<City?>(null) }
    val openMap = { city: City? -> mapFocus = city; mapOpen = true }
    val homeDate = now.atZone(home.zone).toLocalDate()
    // At least the screen's height, so the empty state can sit in the middle of the space under the map.
    BoxWithConstraints(modifier.fillMaxSize()) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.cities_count, others.size, others.size), style = smallText(12), modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Plus, stringResource(R.string.add_city)) { openMap(null) }
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
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(56.dp).clip(Cookie).background(Ink.purple.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Globe, null, tint = Ink.accent, modifier = Modifier.size(26.dp))
                }
                Text(stringResource(R.string.no_cities_title), style = sans(20, 500), modifier = Modifier.padding(top = 14.dp))
                Text(
                    stringResource(R.string.no_cities), style = sans(14, color = Ink.muted), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                )
            }
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
                    .clickable(onClickLabel = stringResource(R.string.city_map)) { openMap(city) }
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
    if (mapOpen) CityMap(mapFocus, onClose = { mapOpen = false })
    }
}

private val BubbleWidth = 46.dp
private val BubbleHeight = 20.dp
private val Pin = 14.dp

/**
 * The night-blue card: the land, with the night side shaded where the sun is down now, every city as a
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
    val context = LocalContext.current
    val land by produceState(Land.loaded()) { value = withContext(Dispatchers.IO) { Land.load(context) } }
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
                val shade = nightShade(subsolarPoint(now))
                onDrawBehind {
                    drawRect(sky)
                    drawRect(glow)
                    translate(origin.x, origin.y) {
                        scale(w / 360, w / 360, pivot = Offset.Zero) {
                            land?.let { drawPath(it.coarse, LandColor) }
                            drawPath(shade, NightColor)
                        }
                    }
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

/** Places this element centred on ([x], [y]). */
private fun Modifier.pinAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place((x.toPx() - p.width / 2f).roundToInt(), (y.toPx() - p.height / 2f).roundToInt()) }
}
