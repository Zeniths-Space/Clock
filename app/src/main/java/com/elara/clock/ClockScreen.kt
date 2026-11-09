package com.elara.clock

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/** Big thin digits for the time, hours over minutes. */
private val BigDigits = outfit(164, weight = 200, spacing = -8f)

/** Home: the time where you are, the day (with its Nepali date), the sun, and other cities a tap away. */
@Composable
fun ClockScreen(modifier: Modifier = Modifier) {
    val now by rememberNow()
    val home = remember { Cities.home() }
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    val here = now.atZone(home.zone)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) {}
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (home.country.isEmpty()) home.name else "${home.name}, ${home.country}", style = outfit(16, 500))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Chevron, null, tint = Ink.text, modifier = Modifier.size(16.dp))
            }
            GlassIconButton(Icons.Search, stringResource(R.string.search_cities))
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Sliders, stringResource(R.string.settings))
        }

        Row(Modifier.padding(top = 14.dp).height(300.dp)) {
            val spoken = here.hm(twentyFour)
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.Center,
            ) {
                Row {
                    Digits(here.format(if (twentyFour) H24 else H12), BigDigits, 138.dp)
                    if (!twentyFour) {
                        Text(if (here.hour < 12) "AM" else "PM", style = outfit(15, 500, Ink.accent), modifier = Modifier.padding(start = 6.dp, top = 16.dp))
                    }
                }
                Digits(here.format(MM), BigDigits, 138.dp)
            }
            TimeZoneStrip(home, now)
        }

        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TodayTile(home, now, Modifier.weight(1f))
            SunTile(home, now, Modifier.weight(1f))
        }

        CityChips(home, now, twentyFour)
    }
}

private val H24 = DateTimeFormatter.ofPattern("HH")
private val H12 = DateTimeFormatter.ofPattern("hh")
private val MM = DateTimeFormatter.ofPattern("mm")

/** The thin line through your time zone on the dotted map, with your city marked on it. */
@Composable
private fun TimeZoneStrip(home: City, now: Instant) {
    val utc = utcLabel(home.zone, now)
    Box(Modifier.width(130.dp).fillMaxHeight().clearAndSetSemantics { contentDescription = utc }) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .clipToBounds()
                .drawWithCache {
                    // The map at 900 dp wide (as in the design), slid so the city sits on the line, 66 dp in.
                    val mapW = 900.dp.toPx()
                    val mapH = mapW / WorldMap.ASPECT
                    val cityY = 99.dp.toPx()
                    val origin = Offset(66.dp.toPx() - WorldMap.x(home.lon) * mapW, cityY - WorldMap.y(home.lat) * mapH)
                    val points = worldMapPoints(origin, mapW)
                    val dot = 2.6.dp.toPx()
                    onDrawBehind { drawWorldMap(points, Ink.mapDot, dot) }
                },
        )
        Text(stringResource(R.string.time_zone), style = outfit(13, 500), modifier = Modifier.padding(start = 18.dp, top = 8.dp))
        Box(Modifier.padding(start = 66.dp, top = 36.dp, bottom = 36.dp).width(1.dp).fillMaxHeight().background(Ink.text))
        Box(
            Modifier
                .padding(start = 59.dp, top = 116.dp)
                .size(25.dp)
                .background(Ink.purple.copy(alpha = 0.22f), CircleShape)
                .padding(5.dp)
                .background(Ink.purple, CircleShape),
        )
        Text(utc, style = mono(12, Ink.text), modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 8.dp))
    }
}

private val DayName = DateTimeFormatter.ofPattern("EEE,")
private val DayMonth = DateTimeFormatter.ofPattern("d MMMM")

@Composable
private fun TodayTile(home: City, now: Instant, modifier: Modifier) {
    val date = now.atZone(home.zone).toLocalDate()
    val bs = remember(date) { Bs.fromAd(date) }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .height(158.dp)
            .shadow(16.dp, shape, ambientColor = Ink.purpleDeep.copy(alpha = 0.3f), spotColor = Ink.purpleDeep.copy(alpha = 0.4f))
            .background(PurpleRamp, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.today), style = outfit(14, 500, Color.White), modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Calendar, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column {
            Text(date.format(DayName), style = outfit(21, 500, Color.White).copy(lineHeight = 1.15.em))
            Text(date.format(DayMonth), style = outfit(21, 500, Color.White).copy(lineHeight = 1.15.em))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TileChip(stringResource(R.string.week, date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)), Color.White.copy(alpha = 0.22f), Color.White)
            // The BS date in Nepali, as Nepali calendars print it.
            bs?.let { TileChip("${Bs.monthName(it.month, true)} ${Bs.digits(it.day)}", Color.White.copy(alpha = 0.22f), Color.White) }
        }
    }
}

@Composable
private fun SunTile(home: City, now: Instant, modifier: Modifier) {
    val date = now.atZone(home.zone).toLocalDate()
    val sun = remember(date, home) { sunTimes(date, home.lat, home.lon, home.zone) }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.height(158.dp).glass(shape, elevation = 14.dp).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sun), style = outfit(14, 500), modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).background(Ink.chipBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Sun, null, tint = Ink.accent, modifier = Modifier.size(17.dp))
            }
        }
        if (sun != null) {
            val (rise, set) = sun
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sunrise), style = outfit(11, color = Ink.muted))
                    Text(rise.format(HourMinute), style = outfit(19, 500))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.sunset), style = outfit(11, color = Ink.muted))
                    Text(set.format(HourMinute), style = outfit(19, 500))
                }
            }
            val light = ChronoUnit.MINUTES.between(rise, set)
            TileChip(stringResource(R.string.daylight, light / 60, light % 60), Ink.chipBg, Ink.chipFg)
        }
    }
}

@Composable
private fun TileChip(text: String, bg: Color, fg: Color) {
    Text(text, style = outfit(11, color = fg), modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

/** Your city large in the middle, two others each side; they spill past the screen edges, as in the design. */
@Composable
private fun CityChips(home: City, now: Instant, twentyFour: Boolean) {
    val others = Cities.all.filter { it != home }
    val row = others.take(2) + home + others.drop(2).take(2)
    Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.wrapContentWidth(unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.forEachIndexed { i, city ->
                val active = city == home
                val size = when {
                    active -> 92.dp
                    i == 1 || i == 3 -> 66.dp
                    else -> 58.dp
                }
                val time = now.atZone(city.zone).hm(twentyFour)
                Column(
                    Modifier
                        .size(size)
                        .glass(CircleShape, alpha = if (active) 0.85f else 0.45f, elevation = if (active) 12.dp else 6.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = city.name) {}
                        .semantics(mergeDescendants = true) { contentDescription = "${city.name} $time" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(city.code, style = outfit(if (active) 22 else 15, 500, if (active) Ink.accent else Ink.text))
                    Text(time, style = outfit(10, color = Ink.muted))
                }
            }
        }
    }
}
