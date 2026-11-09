package com.elara.clock

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
val RowDigits = outfit(132, weight = 700, spacing = -8f)

private val Presets = listOf(1, 3, 5, 10, 15, 25, 45)

/**
 * One countdown. This first version runs it only in the app (nothing rings when it ends); the alarm
 * engine adds the alert, the notification and surviving the app being closed.
 */
@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    var durationMs by rememberSaveable { mutableLongStateOf(10 * 60_000L) }
    var leftMs by rememberSaveable { mutableLongStateOf(durationMs) }
    var endsAt by rememberSaveable { mutableLongStateOf(0L) } // SystemClock.elapsedRealtime when running, 0 when paused.
    val running = endsAt != 0L
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // While running: wake at each whole second left, no more often.
    LaunchedEffect(running) {
        while (running) {
            tick = SystemClock.elapsedRealtime()
            val left = endsAt - tick
            if (left <= 0) {
                leftMs = 0; endsAt = 0; break
            }
            delay(left % 1000 + 1)
        }
    }
    val shown = if (running) (endsAt - tick).coerceAtLeast(0) else leftMs
    val total = (shown + 999) / 1000
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val ends = now.plusNanos(shown * 1_000_000)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_timer), style = outfit(22, 500))
                Text(stringResource(R.string.timer_ends, ends.hm(DateFormat.is24HourFormat(LocalContext.current)), clockText(durationMs / 1000)), style = mono(11))
            }
            GlassIconButton(Icons.List, stringResource(R.string.timer_list))
        }
        Column(
            Modifier
                .padding(top = 14.dp)
                .drawBehind { drawLine(Ink.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .semantics(mergeDescendants = true) { contentDescription = clockText(total) },
        ) {
            StackRow((total / 3600).toString().padStart(2, '0'), stringResource(R.string.unit_h), Ink.wash1)
            StackRow((total / 60 % 60).toString().padStart(2, '0'), stringResource(R.string.unit_min), Ink.purpleLight)
            StackRow((total % 60).toString().padStart(2, '0'), stringResource(R.string.unit_sec), Ink.text)
        }

        PresetDial(minutes) { m ->
            minutes = m
            durationMs = m * 60_000L
            leftMs = durationMs
            endsAt = 0
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Reset, stringResource(R.string.reset), size = 60.dp, iconSize = 22.dp) {
                leftMs = durationMs; endsAt = 0
            }
            MainButton(
                if (running) Icons.Pause else Icons.Play,
                stringResource(if (running) R.string.pause else R.string.start),
            ) {
                if (running) {
                    leftMs = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0); endsAt = 0
                } else if (leftMs > 0) {
                    endsAt = SystemClock.elapsedRealtime() + leftMs
                }
            }
            val addLabel = stringResource(R.string.add_minute)
            Box(
                Modifier
                    .size(60.dp)
                    .glass(CircleShape, elevation = 8.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = addLabel) {
                        if (running) endsAt += 60_000 else leftMs += 60_000
                        durationMs += 60_000
                    }
                    .semantics { contentDescription = addLabel },
                contentAlignment = Alignment.Center,
            ) { Text("+1m", style = mono(12, Ink.text)) }
        }
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
        Text(unit, style = mono(11), modifier = Modifier.padding(top = 10.dp))
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
        Text(label, style = outfit(17, 500, Color.White))
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
                style = mono(if (on) 15 else 11, if (on) Ink.text else Ink.muted, weight = if (on) 500 else 400),
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
