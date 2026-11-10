package com.elara.clock

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
private val BigDigits get() = sans(164, weight = 200, spacing = -8f) // A getter: the colour follows the theme, which can change while the app runs.

/** Home: the time where you are, the day (with its Nepali date), the sun, and other cities a tap away. */
@Composable
fun ClockScreen(modifier: Modifier = Modifier) {
    val now by rememberNow()
    val context = LocalContext.current
    val yours = remember(Settings.get(context).homeCity) { Cities.home(context) }
    val twentyFour = rememberTwentyFour()
    // The city the page shows: your own unless you picked another from the menu or the chips below.
    val cities = (listOf(yours) + Cities.chosen(context)).distinctBy { it.name + it.zone.id }
    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    val home = cities.firstOrNull { shownKey != null && it.name + "|" + it.zone.id == shownKey } ?: yours
    val show = { city: City -> shownKey = if (city.same(yours)) null else city.name + "|" + city.zone.id }
    var menu by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var zones by rememberSaveable { mutableStateOf(false) }
    val here = now.atZone(home.zone)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .onGloballyPositioned { anchor = it.boundsInWindow() }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.DropdownList, onClickLabel = stringResource(R.string.choose_city)) { menu = anchor }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (home.country.isEmpty()) home.name else "${home.name}, ${home.country}", style = sans(16, 500))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Chevron, null, tint = Ink.text, modifier = Modifier.size(16.dp))
                }
            }
            GlassIconButton(Icons.Zones, stringResource(R.string.time_zones)) { zones = true }
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Sliders, stringResource(R.string.settings)) { settings = true }
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
                        Text(if (here.hour < 12) "AM" else "PM", style = sans(15, 500, Ink.accent), modifier = Modifier.padding(start = 6.dp, top = 16.dp))
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

        CityChips(home, cities, now, twentyFour, show)
    }
    menu?.let { at ->
        val cityMap = LocalCityMap.current
        CityMenu(at, cities, home, yours, now, twentyFour, onPick = { show(it); menu = null }, onAdd = { menu = null; cityMap.show(null) }) { menu = null }
    }
    if (settings) Overlay { SettingsPage(onClose = { settings = false }) }
    if (zones) Overlay { TimeZonesPage(onClose = { zones = false }) }
}

/**
 * The city menu under the page's title, as the Elara dropdowns are: as wide as the title (at least 260 dp),
 * 8 dp below it, rounded 24 dp, in the raised colour; the shown city filled purple with a check. Your city
 * first, then the ones you added, then Add city. Drawn over the app, not in a popup window.
 */
@Composable
private fun CityMenu(
    at: androidx.compose.ui.geometry.Rect, cities: List<City>, shown: City, yours: City, now: Instant, twentyFour: Boolean,
    onPick: (City) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit,
) = Overlay {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(at.left.toInt(), (at.bottom + 8.dp.toPx()).toInt()) }
                .width(with(density) { at.width.toDp() }.coerceAtLeast(260.dp))
                .shadow(16.dp, shape, ambientColor = Ink.night.copy(alpha = 0.2f), spotColor = Ink.night.copy(alpha = 0.3f))
                .background(if (Ink.dark) Color(0xFF1F1F1F) else Color.White, shape)
                .clip(shape)
                .pointerInput(Unit) {}
                .padding(vertical = 6.dp),
        ) {
            cities.forEach { city ->
                val on = city.same(shown)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) Ink.purple.copy(alpha = 0.22f) else Color.Transparent)
                        .selectable(on, role = Role.RadioButton) { onPick(city) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(city.name, style = sans(16, 500, if (on) Ink.accent else Ink.text))
                        Text(
                            if (city.same(yours)) stringResource(R.string.your_city) else city.country,
                            style = sans(12, color = Ink.muted),
                        )
                    }
                    Text(now.atZone(city.zone).hm(twentyFour), style = sans(15, color = if (on) Ink.accent else Ink.text))
                    if (on) Icon(Icons.Check, null, tint = Ink.accent, modifier = Modifier.padding(start = 10.dp).size(18.dp))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Plus, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.add_city), style = sans(15, 500, Ink.accent))
            }
        }
    }
}

private val H24 = DateTimeFormatter.ofPattern("HH")
private val H12 = DateTimeFormatter.ofPattern("hh")
private val MM = DateTimeFormatter.ofPattern("mm")

/** The thin line through your time zone on the dotted map, with your city marked on it. */
@Composable
private fun TimeZoneStrip(home: City, now: Instant) {
    val utc = utcLabel(home.zone, now)
    // Everything is centred on one vertical line down the middle of the strip: the label above it, the
    // city's dot on it (at the city's place on the map), and the UTC offset below it.
    Box(Modifier.width(StripWidth).fillMaxHeight().clearAndSetSemantics { contentDescription = utc }) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(vertical = MapInset)
                .clipToBounds()
                // Kept as a picture: thousands of dots, redrawn only when the home city changes.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    // The map at 900 dp wide (as in the design), slid so the city sits on the line.
                    val mapW = 900.dp.toPx()
                    val mapH = mapW / WorldMap.ASPECT
                    val origin = Offset(size.width / 2 - WorldMap.x(home.lon) * mapW, (CityY - MapInset).toPx() - WorldMap.y(home.lat) * mapH)
                    val points = worldMapPoints(origin, mapW)
                    val dot = 2.6.dp.toPx()
                    onDrawBehind { drawWorldMap(points, Ink.mapDot, dot) }
                },
        )
        Text(stringResource(R.string.time_zone), style = sans(13, 500), modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        Box(Modifier.align(Alignment.TopCenter).padding(top = 36.dp, bottom = 36.dp).width(1.dp).fillMaxHeight().background(Ink.text))
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = CityY - 12.5.dp)
                .size(25.dp)
                .background(Ink.purple.copy(alpha = 0.22f), CircleShape)
                .padding(5.dp)
                .background(Ink.purple, CircleShape),
        )
        Text(utc, style = smallText(12, Ink.text), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
}

private val StripWidth = 130.dp
/** The map is drawn this far in from the strip's top and bottom. */
private val MapInset = 24.dp
/** How far down the strip the city (its dot on the line) sits. */
private val CityY = 123.dp

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
            Text(stringResource(R.string.today), style = sans(14, 500, Color.White), modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Calendar, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column {
            Text(date.format(DayName), style = sans(21, 500, Color.White).copy(lineHeight = 1.15.em))
            Text(date.format(DayMonth), style = sans(21, 500, Color.White).copy(lineHeight = 1.15.em))
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
            Text(stringResource(R.string.sun), style = sans(14, 500), modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).background(Ink.chipBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Sun, null, tint = Ink.accent, modifier = Modifier.size(17.dp))
            }
        }
        if (sun != null) {
            val (rise, set) = sun
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sunrise), style = sans(11, color = Ink.muted))
                    Text(rise.format(HourMinute), style = sans(19, 500))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.sunset), style = sans(11, color = Ink.muted))
                    Text(set.format(HourMinute), style = sans(19, 500))
                }
            }
            val light = ChronoUnit.MINUTES.between(rise, set)
            TileChip(stringResource(R.string.daylight, light / 60, light % 60), Ink.chipBg, Ink.chipFg)
        }
    }
}

@Composable
private fun TileChip(text: String, bg: Color, fg: Color) {
    Text(text, style = sans(11, color = fg), modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

/**
 * The shown city large in the middle, two others each side; they spill past the screen edges, as in the
 * design. Tapping one shows that city on the page.
 */
@Composable
private fun CityChips(home: City, cities: List<City>, now: Instant, twentyFour: Boolean, onPick: (City) -> Unit) {
    val others = cities.filterNot { it.same(home) }
    val row = others.take(2) + home + others.drop(2).take(2)
    Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.wrapContentWidth(unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.forEachIndexed { i, city ->
                val active = city.same(home)
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
                        .clickable(role = Role.Button, onClickLabel = city.name) { onPick(city) }
                        .semantics(mergeDescendants = true) { contentDescription = "${city.name} $time" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(city.code, style = sans(if (active) 22 else 15, 500, if (active) Ink.accent else Ink.text))
                    Text(time, style = sans(10, color = Ink.muted))
                }
            }
        }
    }
}
