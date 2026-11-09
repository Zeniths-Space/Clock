package com.elara.clock

import android.text.format.DateFormat
import androidx.compose.foundation.background
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
    val now by rememberNow()
    val home = remember { Cities.home() }
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    val others = remember(home) { Cities.all.filter { it != home } }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cities_count, Cities.all.size), style = mono(12), modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Plus, stringResource(R.string.add_city))
        }
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.your_cities))
                append("\n")
                withStyle(SpanStyle(color = Ink.soft)) { append(stringResource(R.string.around_world)) }
            },
            style = outfit(34, 400, spacing = -0.5f).copy(lineHeight = 1.08.em),
            modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
        )

        MapCard(home, Cities.all, now, twentyFour)

        others.forEach { city ->
            val there = now.atZone(city.zone)
            val diff = offsetBetween(home.zone, city.zone, now)
            val diffText = when {
                diff.isZero -> stringResource(R.string.same_time)
                diff.isNegative -> stringResource(R.string.behind, spanText(diff))
                else -> stringResource(R.string.ahead, spanText(diff))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(Ink.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(horizontal = 4.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(utcLabel(city.zone, now), style = mono(11))
                    Text(city.name, style = outfit(17, 500), modifier = Modifier.padding(vertical = 3.dp))
                    Text(diffText, style = outfit(12, color = Ink.muted))
                }
                Text(there.hm(twentyFour), style = outfit(44, 300, spacing = -1.5f))
                if (!twentyFour) Text(if (there.hour < 12) "am" else "pm", style = outfit(13, 500, Ink.muted), modifier = Modifier.padding(start = 4.dp, top = 14.dp))
            }
        }
    }
}

/** The night-blue card: every city as a dot on the dotted map, with its time in a bubble above it. */
@Composable
private fun MapCard(home: City, cities: List<City>, now: java.time.Instant, twentyFour: Boolean) {
    val shape = RoundedCornerShape(30.dp)
    val mapWidth = 380.dp
    val mapLeft = (-15).dp
    val mapTop = 36.dp
    val mapHeight = mapWidth / WorldMap.ASPECT
    Box(
        Modifier
            .fillMaxWidth()
            .height(214.dp)
            .shadow(18.dp, shape, ambientColor = Ink.text.copy(alpha = 0.3f), spotColor = Ink.text.copy(alpha = 0.3f))
            .clip(shape)
            .drawWithCache {
                val sky = Brush.linearGradient(listOf(Ink.night, Ink.nightDeep), Offset(0f, 0f), Offset(size.width * 0.3f, size.height))
                val glow = Brush.radialGradient(listOf(Color(0xFF4A2BB0), Color(0x004A2BB0)), Offset(size.width * 0.7f, 0f), size.width * 0.8f)
                val points = worldMapPoints(Offset(mapLeft.toPx(), mapTop.toPx()), mapWidth.toPx())
                val dot = 1.7.dp.toPx()
                onDrawBehind {
                    drawRect(sky)
                    drawRect(glow)
                    drawWorldMap(points, Ink.lavender.copy(alpha = 0.45f), dot)
                }
            }
            .clearAndSetSemantics {},
    ) {
        // Bubbles that would overlap one already shown are left out (Delhi, Dubai and Kathmandu sit close); home's comes first.
        val bubbled = mutableListOf<Dp>()
        (listOf(home) + cities.filter { it != home }).forEach { city ->
            val x = mapLeft + mapWidth * WorldMap.x(city.lon)
            val y = mapTop + mapHeight * WorldMap.y(city.lat)
            val isHome = city == home
            Box(
                Modifier
                    .pinAt(x, y, 0.dp)
                    .size(18.dp)
                    .background((if (isHome) Ink.lilac else Color.White).copy(alpha = if (isHome) 0.35f else 0.18f), CircleShape)
                    .padding(4.dp)
                    .background(if (isHome) Ink.lilac else Color.White, CircleShape),
            )
            if (bubbled.any { abs((it - x).value) < 46f }) return@forEach
            bubbled += x
            Text(
                now.atZone(city.zone).hm(twentyFour),
                style = mono(10, if (isHome) Color.White else Ink.night, weight = 500),
                modifier = Modifier
                    .pinAt(x, y, 14.dp)
                    .background(if (isHome) Ink.purple else Ink.lavender, RoundedCornerShape(9.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Text(
            stringResource(R.string.map_now, now.atZone(home.zone).hm(twentyFour), home.name),
            style = outfit(12, color = Ink.lavender),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp),
        )
    }
}

/** Places this element centred on ([x], [y]), or with its bottom [above] that point when [above] > 0. */
private fun Modifier.pinAt(x: Dp, y: Dp, above: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        val left = x.toPx() - p.width / 2f
        val top = if (above > 0.dp) y.toPx() - above.toPx() - p.height else y.toPx() - p.height / 2f
        p.place(left.roundToInt(), top.roundToInt())
    }
}
