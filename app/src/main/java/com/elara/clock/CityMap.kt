package com.elara.clock

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.Instant

/** Map degrees: x = lon + 180 (0 to 360), y = 80 - lat (0 to 140), the frame of [Land]. */
private fun City.spot() = Offset((lon + 180).toFloat(), (WorldMap.NORTH - lat).toFloat())

/**
 * The city map, full screen: the world in dots, zoomed in on [focus] (or your own city), with every city the
 * app knows as a dot you can tap. Drag to move, pinch or double-tap to zoom. The header has Back, a search
 * that flies the map to a city, and Remove when the chosen city is in your world clock; the card at the
 * bottom shows the chosen city's time and adds it.
 */
@Composable
fun CityMap(focus: City?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The page is night blue in both themes, so the status bar icons stay light.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        CityMapPage(focus, onClose)
    }
}

@Composable
private fun CityMapPage(focus: City?, onClose: () -> Unit) {
    val context = LocalContext.current
    val catalog by produceState(emptyList<City>()) { value = withContext(Dispatchers.IO) { Cities.catalog(context) } }
    val land by produceState(emptyList<Offset>()) { value = withContext(Dispatchers.IO) { Land.cells(context) } }
    val home = remember { Cities.home(context) }
    val chosen = Cities.chosen(context)
    var selected by remember { mutableStateOf(focus ?: home) }
    var query by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf(false) }
    val now by rememberNow()
    val twentyFour = DateFormat.is24HourFormat(context)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    BackHandler(query.isNotEmpty()) { query = ""; focusManager.clearFocus() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink.night, Ink.nightDeep))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // k is pixels per degree: from the whole world across the screen to about 25 degrees across.
        val minK = w / 360
        val maxK = w / 25
        var k by remember { mutableFloatStateOf(w / 70) }
        var center by remember { mutableStateOf(selected.spot()) }
        fun clamp(c: Offset, k: Float): Offset {
            // Any place can be centred (so the north and the far east too), but the map never leaves the screen.
            val halfW = w / 2 / k
            return Offset(if (halfW * 2 >= 360) 180f else c.x.coerceIn(0f, 360f), c.y.coerceIn(0f, 140f))
        }
        fun toScreen(p: Offset) = Offset((p.x - center.x) * k + w / 2, (p.y - center.y) * k + h / 2)
        fun flyTo(city: City) {
            selected = city
            val from = center
            val to = clamp(city.spot(), k)
            scope.launch { animate(0f, 1f, animationSpec = spring(stiffness = 200f)) { t, _ -> center = from + (to - from) * t } }
        }

        val sun = remember(now) { subsolarPoint(now) }
        // As flat x,y arrays: drawPoints with a List records one point at a time, too slow for 16 000 dots.
        val (dayLand, nightLand) = remember(land, sun) {
            land.partition { isDay(80.0 - it.y, it.x - 180.0, sun) }.let { (d, n) -> d.toXy() to n.toXy() }
        }
        val dayPaint = remember { Paint().apply { strokeCap = StrokeCap.Round } }.also { it.color = Ink.lavender.copy(alpha = 0.55f) }
        val nightPaint = remember { Paint().apply { strokeCap = StrokeCap.Round } }.also { it.color = Ink.lavender.copy(alpha = 0.2f) }
        // Cities in label order, worked out once: yours, then large cities, then the rest.
        val mine = chosen + home
        val ranked = remember(catalog, mine) {
            val (yours, rest) = catalog.partition { c -> mine.any { it.same(c) } }
            yours + rest.sortedByDescending { it.major }
        }
        val mineCount = remember(ranked) { ranked.count { c -> mine.any { it.same(c) } } }
        val majorEnd = remember(ranked) { ranked.indexOfLast { it.major }.coerceAtLeast(mineCount - 1) + 1 }
        val rankedSpots = remember(ranked) { ranked.map { it.spot() } }
        val measurer = rememberTextMeasurer(cacheSize = 8)
        val layouts = remember(ranked) { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
        val dp = androidx.compose.ui.platform.LocalDensity.current.density
        val labelStyle = TextStyle(fontSize = 11.sp, color = Ink.lavender)
        val chosenStyle = TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)

        // The land and cities: moving the map only moves this layer; it is drawn again only when the zoom or
        // the chosen city changes.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = w / 2 - center.x * k
                    translationY = h / 2 - center.y * k
                },
        ) {
            val dot = (k * 0.5f).coerceIn(1.6f * dp, 6f * dp)
            nightPaint.strokeWidth = dot / k
            dayPaint.strokeWidth = dot / k
            scale(k, k, pivot = Offset.Zero) {
                drawIntoCanvas {
                    it.drawRawPoints(PointMode.Points, nightLand, nightPaint)
                    it.drawRawPoints(PointMode.Points, dayLand, dayPaint)
                }
            }
            // Cities are drawn in this layer too, at unscaled sizes, so dragging never draws them again.
            fun inLayer(spot: Offset) = spot * k
            // Cities: every one a small dot; yours lilac; names where they fit, larger cities first.
            ranked.forEachIndexed { i, c ->
                val p = inLayer(rankedSpots[i])
                if (i < mineCount) {
                    drawCircle(Ink.lilac.copy(alpha = 0.3f), 7 * dp, p)
                    drawCircle(Ink.lilac, 3.5f * dp, p)
                } else {
                    drawCircle(Color.White.copy(alpha = 0.75f), 2.2f * dp, p)
                }
            }
            val sel = inLayer(selected.spot())
            drawCircle(Ink.purple.copy(alpha = 0.3f), 16 * dp, sel)
            drawCircle(Ink.purple, 8 * dp, sel)
            drawCircle(Color.White, 3 * dp, sel)

            val taken = mutableListOf<Rect>(Rect(sel, 18 * dp))
            fun label(c: City, p: Offset, isSel: Boolean, mine: Boolean): Boolean {
                val layout = layouts.getOrPut(c.name + c.zone.id + isSel) {
                    measurer.measure(c.name, if (isSel || mine) chosenStyle.copy(fontSize = if (isSel) 14.sp else 12.sp) else labelStyle)
                }
                val size = androidx.compose.ui.geometry.Size(layout.size.width.toFloat(), layout.size.height.toFloat())
                val gap = (if (isSel) 18 else 7) * dp
                // Below the dot, else above, right or left of it: the first place clear of other names.
                val top = listOf(
                    Offset(p.x - size.width / 2, p.y + gap), Offset(p.x - size.width / 2, p.y - gap - size.height),
                    Offset(p.x + gap, p.y - size.height / 2), Offset(p.x - gap - size.width, p.y - size.height / 2),
                ).firstOrNull { at -> isSel || taken.none { it.overlaps(Rect(at, size).inflate(3 * dp)) } } ?: return false
                taken += Rect(top, size).inflate(3 * dp)
                drawText(layout, topLeft = top)
                return true
            }
            label(selected, sel, isSel = true, mine = true)
            val smallNames = k >= w / 40 // Small places get names only when zoomed in.
            for (i in ranked.indices) {
                val c = ranked[i]
                if (i >= majorEnd && !smallNames) break
                if (c.same(selected)) continue
                val p = inLayer(rankedSpots[i])
                label(c, p, isSel = false, mine = i < mineCount)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newK = (k * zoom).coerceIn(minK, maxK)
                        // Keep the point under the fingers still while zooming, then follow the drag.
                        val under = center + (centroid - Offset(w / 2, h / 2)) / k
                        center = clamp(under - (centroid - Offset(w / 2, h / 2)) / newK - pan / newK, newK)
                        k = newK
                    }
                }
                .pointerInput(catalog) {
                    detectTapGestures(
                        onDoubleTap = { at ->
                            val from = k
                            val to = (k * 2).coerceAtMost(maxK)
                            val under = center + (at - Offset(w / 2, h / 2)) / k
                            scope.launch {
                                animate(from, to, animationSpec = spring(stiffness = 400f)) { v, _ ->
                                    k = v
                                    center = clamp(under - (at - Offset(w / 2, h / 2)) / v, v)
                                }
                            }
                        },
                        onTap = { at ->
                            // The nearest city within 28 dp of the tap.
                            catalog.minByOrNull { (toScreen(it.spot()) - at).getDistanceSquared() }
                                ?.takeIf { (toScreen(it.spot()) - at).getDistance() < 28 * dp }
                                ?.let { focusManager.clearFocus(); query = ""; flyTo(it) }
                        },
                    )
                }
                .semantics { contentDescription = selected.name },
        )

        // Header: back, search, and remove for a city in your world clock.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(Icons.Back, stringResource(R.string.back), size = 48.dp, iconSize = 20.dp, onClick = onClose)
                SearchPill(query, Modifier.weight(1f)) { query = it.take(40) }
                if (chosen.any { it.same(selected) } && !selected.same(home)) {
                    GlassIconButton(Icons.Trash, stringResource(R.string.remove), size = 48.dp, iconSize = 20.dp) { removing = true }
                }
            }
            if (query.isNotBlank()) {
                val q = query.fold()
                val results = catalog.filter { it.name.fold().contains(q) || it.country.fold().contains(q) }
                    .sortedBy { if (it.name.fold().startsWith(q)) 0 else 1 }
                    .take(6)
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp), elevation = 10.dp).padding(vertical = 6.dp)) {
                    if (results.isEmpty()) {
                        Text(stringResource(R.string.no_city_found), style = sans(14, color = Ink.muted), modifier = Modifier.padding(18.dp))
                    }
                    results.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { focusManager.clearFocus(); query = ""; flyTo(c) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = sans(16, 500))
                                Text(listOf(c.country, utcLabel(c.zone, now)).filter(String::isNotEmpty).joinToString(" · "), style = sans(12, color = Ink.muted))
                            }
                            Text(now.atZone(c.zone).hm(twentyFour), style = sans(15))
                        }
                    }
                }
            }
        }

        CityCard(
            selected, home, now, twentyFour,
            added = chosen.any { it.same(selected) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp),
        ) {
            Cities.add(context, selected)
            onClose()
        }
    }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            containerColor = if (Ink.dark) Color(0xFF1F1F1F) else Color.White,
            title = { Text(stringResource(R.string.remove_city, selected.name), style = sans(20, 500)) },
            text = { Text(stringResource(R.string.remove_city_text), style = sans(15, color = Ink.muted)) },
            confirmButton = {
                TextButton(onClick = { Cities.remove(context, selected); removing = false; onClose() }) {
                    Text(stringResource(R.string.remove), style = sans(15, 500, if (Ink.dark) Color(0xFFFF8A8A) else Color(0xFFD93A3A)))
                }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel), style = sans(15, 500, Ink.accent)) } },
        )
    }
}

private fun List<Offset>.toXy() = FloatArray(size * 2).also { a -> forEachIndexed { i, p -> a[2 * i] = p.x; a[2 * i + 1] = p.y } }

/** Lower case without accents, so "sao" finds São Paulo. */
private fun String.fold() = Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
private fun SearchPill(value: String, modifier: Modifier, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val hint = stringResource(R.string.search_cities)
    Row(
        modifier.height(48.dp).glass(shape, elevation = 8.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Search, null, tint = Ink.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = sans(15, color = Ink.muted))
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = sans(15),
                cursorBrush = SolidColor(Ink.purple),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Close, stringResource(R.string.clear_search), tint = Ink.muted,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onChange("") }.padding(5.dp),
            )
        }
    }
}

/** The chosen city: its name, zone, the time there against yours, and Add (or where it already is). */
@Composable
private fun CityCard(city: City, home: City, now: Instant, twentyFour: Boolean, added: Boolean, modifier: Modifier, onAdd: () -> Unit) {
    val there = now.atZone(city.zone)
    val diff = offsetBetween(home.zone, city.zone, now)
    val day = there.toLocalDate().compareTo(now.atZone(home.zone).toLocalDate()).let {
        stringResource(if (it < 0) R.string.yesterday else if (it > 0) R.string.tomorrow else R.string.today)
    }
    val relation = when {
        city.same(home) -> stringResource(R.string.your_city)
        diff.isZero -> "$day · ${stringResource(R.string.same_time)}"
        diff.isNegative -> "$day · ${stringResource(R.string.behind, spanText(diff))}"
        else -> "$day · ${stringResource(R.string.ahead, spanText(diff))}"
    }
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), elevation = 14.dp).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(city.name, style = sans(24, 500))
                Text(listOf(city.country, utcLabel(city.zone, now)).filter(String::isNotEmpty).joinToString(" · "), style = sans(13, color = Ink.muted))
                Text(relation, style = sans(13, 500, Ink.accent), modifier = Modifier.padding(top = 6.dp))
            }
            Text(there.hm(twentyFour), style = sans(40, 300, spacing = -1f))
            if (!twentyFour) Text(if (there.hour < 12) "am" else "pm", style = sans(13, 500, Ink.muted), modifier = Modifier.padding(start = 4.dp, top = 12.dp))
        }
        when {
            city.same(home) -> {}
            added -> Text(
                stringResource(R.string.in_world_clock),
                style = sans(15, 500, Ink.muted),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            else -> PillButton(stringResource(R.string.add_named, city.name), primary = true, Modifier.padding(top = 16.dp).fillMaxWidth(), onClick = onAdd)
        }
    }
}
