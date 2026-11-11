package com.elara.clock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Heavy stacked digits, one per row, as in the design. */
val RowDigits get() = sans(132, weight = 700, spacing = -8f)

private val Presets = listOf(1, 3, 5, 10, 15, 25, 45)

/**
 * The countdowns; their state, end alarms and notifications live in [TimerEngine], so they run with the app
 * closed. The big rows, dial and buttons show one timer; with more than one, chips under the rows show them all
 * and pick which; "+" adds one.
 */
@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timers = TimerEngine.timers(context)
    val timer = timers.current
    val running = timer.running
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // While any runs: wake at the next whole second any of them has left, no more often. TimerEngine ends each
    // when its alarm goes off.
    val ends = timers.list.filter { it.running }.map { it.endsAt }
    LaunchedEffect(ends) {
        while (ends.isNotEmpty()) {
            tick = SystemClock.elapsedRealtime()
            val next = ends.map { it - tick }.filter { it > 0 }.minOfOrNull { it % 1000 + 1 } ?: break
            delay(next)
        }
    }
    val shown = if (running) (timer.endsAt - tick).coerceAtLeast(0) else timer.leftMs
    val durationMs = timer.durationMs
    val total = (shown + 999) / 1000
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val endsAt = now.plusNanos(shown * 1_000_000)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_timer), style = sans(22, 500))
                val ends = stringResource(R.string.timer_ends, endsAt.hm(rememberTwentyFour()), clockText(durationMs / 1000))
                val name = timers.name(stringResource(R.string.tab_timer), timer.id)
                Text(if (timers.list.size > 1) "${name.uppercase()} · $ends" else ends, style = smallText(11))
            }
            GlassIconButton(Icons.List, stringResource(R.string.timer_list))
        }
        Column(
            Modifier
                .padding(top = 14.dp)
                .drawBehind { drawLine(Ink.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .semantics(mergeDescendants = true) { contentDescription = clockText(total) },
        ) {
            StackRow((total / 3600).toString().padStart(2, '0'), stringResource(R.string.unit_h), Ink.faded)
            StackRow((total / 60 % 60).toString().padStart(2, '0'), stringResource(R.string.unit_min), Ink.purpleLight)
            StackRow((total % 60).toString().padStart(2, '0'), stringResource(R.string.unit_sec), Ink.text)
        }

        // Chips once there is more than one timer, or "+" alone while this one runs or waits.
        if (timers.list.size > 1 || !timer.idle) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (timers.list.size > 1) {
                    timers.list.forEach { t ->
                        TimerChip(t, timers.name(stringResource(R.string.tab_timer), t.id), t.id == timer.id, tick) { TimerEngine.select(context, t.id) }
                    }
                }
                GlassIconButton(Icons.Plus, stringResource(R.string.new_timer), size = 44.dp, iconSize = 18.dp) { TimerEngine.add(context) }
            }
        }

        PresetDial(timer.minutes) { TimerEngine.choose(context, it) }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Reset, stringResource(R.string.reset), size = 60.dp, iconSize = 22.dp) {
                TimerEngine.reset(context)
            }
            MainButton(
                if (running) Icons.Pause else Icons.Play,
                stringResource(if (running) R.string.pause else R.string.start),
            ) {
                if (running) {
                    TimerEngine.pause(context)
                } else {
                    // The end rings through a notification: ask for them (Android 13+) the first time.
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Set tick too, or the first frame shows endsAt minus an old tick: a jump, then the real time.
                    tick = SystemClock.elapsedRealtime()
                    TimerEngine.start(context)
                }
            }
            val addLabel = stringResource(R.string.add_minute)
            Box(
                Modifier
                    .size(60.dp)
                    .glass(CircleShape, elevation = 8.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = addLabel) {
                        TimerEngine.addMinute(context)
                    }
                    .semantics { contentDescription = addLabel },
                contentAlignment = Alignment.Center,
            ) { Text("+1m", style = smallText(12, Ink.text)) }
        }
    }
}

/** One timer under the big rows: a ring of the time left, its name and time; the shown one filled purple at 22%. */
@Composable
private fun TimerChip(t: TimerState, name: String, chosen: Boolean, tick: Long, onClick: () -> Unit) {
    val left = if (t.running) (t.endsAt - tick).coerceAtLeast(0) else t.leftMs
    val seconds = (left + 999) / 1000
    val time = if (seconds >= 3600) clockText(seconds) else "%02d:%02d".format(seconds / 60, seconds % 60)
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .height(44.dp)
            .then(
                if (chosen) Modifier.background(Ink.purple.copy(alpha = 0.22f), shape).border(1.dp, Ink.purple.copy(alpha = 0.6f), shape)
                else Modifier.glass(shape, elevation = 4.dp),
            )
            .clip(shape)
            .selectable(chosen, role = Role.Tab, onClick = onClick)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val track = Ink.line
        val accent = if (t.paused) Ink.muted else Ink.accent
        val fraction = if (t.durationMs > 0) (left.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f
        Canvas(Modifier.size(22.dp)) {
            val w = 2.5.dp.toPx()
            drawCircle(track, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
            drawArc(accent, -90f, 360f * fraction, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(name, style = sans(14, 500))
        Text(time, style = sans(14, color = accent))
    }
}

/** "00:10:00" for [seconds]. */
fun clockText(seconds: Long) =
    "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)

/** A full-width row: a big number on the left, its small unit at the top right, a hairline under it. */
@Composable
fun StackRow(value: String, unit: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .drawBehind { drawLine(Ink.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 20.dp),
    ) {
        Digits(value, RowDigits.copy(color = color), 104.dp, Modifier.weight(1f))
        Text(unit, style = smallText(11), modifier = Modifier.padding(top = 10.dp))
    }
}

/** The purple pill: Start or Pause. */
@Composable
fun MainButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .height(60.dp)
            .shadow(14.dp, shape, ambientColor = Ink.purpleDeep.copy(alpha = 0.32f), spotColor = Ink.purpleDeep.copy(alpha = 0.42f))
            .background(PurpleRamp, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = sans(17, 500, Color.White))
    }
}

/**
 * Presets around a dotted half circle, the chosen one at the top under the needle. Choosing another turns
 * the dial to it.
 */
@Composable
private fun PresetDial(selected: Int, onSelect: (Int) -> Unit) {
    val index = Presets.indexOf(selected).coerceAtLeast(0)
    val turn by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.8f, stiffness = 300f), label = "dial")
    Box(Modifier.fillMaxWidth().height(128.dp).clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            // The arc's centre is 260 dp below its top, which sits 40 dp down.
            val r = 260.dp.toPx()
            val c = Offset(size.width / 2, 40.dp.toPx() + r)
            drawCircle(
                Ink.text.copy(alpha = 0.35f), r, c,
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 6.dp.toPx()))),
            )
            drawLine(Ink.text, Offset(size.width / 2, 40.dp.toPx()), Offset(size.width / 2, size.height), 1.5.dp.toPx())
            drawCircle(Ink.purple.copy(alpha = 0.2f), 9.dp.toPx(), Offset(size.width / 2, 40.dp.toPx()))
            drawCircle(Ink.purple, 4.5.dp.toPx(), Offset(size.width / 2, 40.dp.toPx()))
        }
        Presets.forEachIndexed { i, m ->
            val degrees = (i - turn) * 12f
            val on = i == index
            Text(
                "${m}M",
                style = smallText(if (on) 15 else 11, if (on) Ink.text else Ink.muted, weight = if (on) 500 else 400),
                modifier = Modifier
                    .onArc(degrees)
                    .alpha((1f - abs(degrees) / 48f).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onSelect(m) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

/** Places a label just outside the dial's arc at [degrees] from the top, turned to follow it. */
private fun Modifier.onArc(degrees: Float) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val r = 264.dp.toPx()
        val a = Math.toRadians(degrees.toDouble())
        val cx = constraints.maxWidth / 2f
        val cy = 40.dp.toPx() + 260.dp.toPx()
        val x = cx + r * sin(a).toFloat()
        val y = cy - r * cos(a).toFloat()
        // Bottom centre on the point, rotated about it.
        p.placeWithLayer((x - p.width / 2f).roundToInt(), (y - p.height).roundToInt()) {
            rotationZ = degrees
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }
    }
}
