package com.elara.clock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val HeroDigits get() = sans(112, weight = 600, spacing = -6f)

/** [AlarmSheet] open for a new alarm. */
private const val NewAlarm = -1

/** Why alarms might not ring, with where to fix it. */
private enum class RingProblem(val text: Int) { NOTIFICATIONS(R.string.warn_notifications), EXACT(R.string.warn_exact), FULL_SCREEN(R.string.warn_full_screen) }

@Composable
fun AlarmsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val zone = remember { ZoneId.systemDefault() }
    val use24 = rememberTwentyFour()
    val alarms by remember { AlarmEngine.alarms(context) }.collectAsState()
    val here = now.atZone(zone)
    val next = AlarmEngine.next(alarms, here)
    // The alarm open in the sheet: its id, NewAlarm for a new one, null when the sheet is closed.
    var sheet by rememberSaveable { mutableStateOf<Int?>(null) }

    // What could stop alarms ringing, checked again each time the screen comes back (say, from Settings).
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }
    val anyOn = alarms.any { it.on }
    val problem = remember(checks, anyOn) {
        when {
            !anyOn -> null
            !AlarmEngine.canNotify(context) -> RingProblem.NOTIFICATIONS
            !AlarmEngine.canRingOnTime(context) -> RingProblem.EXACT
            !AlarmEngine.canShowFullScreen(context) -> RingProblem.FULL_SCREEN
            else -> null
        }
    }
    // Alarms ring through a notification: ask (Android 13+) the first time one is turned on.
    val ensureCanRing = {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = NavBarSpace + 70.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_alarms), style = sans(30, 500, spacing = -0.5f), modifier = Modifier.weight(1f))
                FormatSwitch(use24) { on -> com.elara.clock.Settings.update(context) { it.copy(timeFormat = if (on) TimeFormat.H24 else TimeFormat.H12) } }
            }
            problem?.let { WarningCard(it) { context.openFix(it) } }
            if (alarms.isEmpty()) {
                EmptyState(use24, onAdd = { sheet = NewAlarm }) { quick ->
                    AlarmEngine.save(context, quick.copy(id = AlarmEngine.newId(context)))
                    ensureCanRing()
                }
            }
            next?.let { (alarm, at) -> NextAlarmCard(alarm, at, here, use24) }
            alarms.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { alarm ->
                        AlarmTile(alarm, hero = next?.first?.id == alarm.id, use24, Modifier.weight(1f), onOpen = { sheet = alarm.id }) { on ->
                            if (on) ensureCanRing()
                            AlarmEngine.setOn(context, alarm.id, on)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        val addLabel = stringResource(R.string.add_alarm)
        // The empty state has its own Add button; the floating one is for adding more.
        if (alarms.isNotEmpty()) Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 104.dp)
                .size(60.dp)
                .shadow(14.dp, CircleShape, ambientColor = Ink.purpleDeep.copy(alpha = 0.35f), spotColor = Ink.purpleDeep.copy(alpha = 0.45f))
                .background(PurpleRamp, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = addLabel) { sheet = NewAlarm }
                .semantics { contentDescription = addLabel },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Plus, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }

    sheet?.let { open ->
        val editing = alarms.firstOrNull { it.id == open }
        // An alarm deleted elsewhere while open (it rang once and was removed): nothing left to edit.
        if (open != NewAlarm && editing == null) {
            SideEffect { sheet = null }
            return@let
        }
        AlarmSheet(
            editing = editing,
            twentyFour = use24,
            onSave = { alarm ->
                AlarmEngine.save(context, if (editing == null) alarm.copy(id = AlarmEngine.newId(context)) else alarm)
                ensureCanRing()
                sheet = null
            },
            onDelete = {
                editing?.let { AlarmEngine.delete(context, it.id) }
                sheet = null
            },
            onClose = { sheet = null },
        )
    }
}

/**
 * No alarms yet: a glass clock with a soft purple glow, a short line, Add alarm, and a few one-tap alarms
 * (6:00, 7:00, or a nap from now).
 */
@Composable
private fun EmptyState(twentyFour: Boolean, onAdd: () -> Unit, onQuick: (Alarm) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(Ink.purple.copy(alpha = if (Ink.dark) 0.35f else 0.28f), Color.Transparent)), size.minDimension / 2)
            }
            Box(Modifier.size(176.dp).glass(CircleShape, alpha = 0.7f, elevation = 18.dp))
            Canvas(Modifier.size(176.dp).clearAndSetSemantics {}) {
                val c = center
                val r = size.minDimension / 2
                for (i in 0 until 12) {
                    val major = i % 3 == 0
                    val a = Math.toRadians(i * 30.0)
                    val outer = r * 0.84f
                    val inner = outer - (if (major) 14.dp else 7.dp).toPx()
                    val dir = Offset(kotlin.math.sin(a).toFloat(), -kotlin.math.cos(a).toFloat())
                    drawLine(if (major) Ink.lilac else Ink.lilac.copy(alpha = 0.5f), c + dir * inner, c + dir * outer, (if (major) 4.dp else 2.5.dp).toPx(), StrokeCap.Round)
                }
                // Seven o'clock: hour hand towards 7, minute hand on 12.
                val hour = Math.toRadians(210.0)
                drawLine(PurpleRamp, c, c + Offset(kotlin.math.sin(hour).toFloat(), -kotlin.math.cos(hour).toFloat()) * (r * 0.42f), 7.dp.toPx(), StrokeCap.Round)
                drawLine(Ink.text, c, c + Offset(0f, -r * 0.62f), 4.5.dp.toPx(), StrokeCap.Round)
                drawCircle(Ink.purple, 8.dp.toPx(), c)
                drawCircle(Color.White, 3.dp.toPx(), c)
            }
        }
        Text(stringResource(R.string.empty_title), style = sans(28, 500), modifier = Modifier.padding(top = 18.dp))
        Text(
            stringResource(R.string.empty_sub),
            style = sans(15, color = Ink.muted),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
        )
        val shape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .padding(top = 24.dp)
                .height(56.dp)
                .shadow(14.dp, shape, ambientColor = Ink.purpleDeep.copy(alpha = 0.32f), spotColor = Ink.purpleDeep.copy(alpha = 0.42f))
                .background(PurpleRamp, shape)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Plus, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.add_alarm), style = sans(17, 500, Color.White))
        }
        Text(stringResource(R.string.quick_add), style = smallText(11), modifier = Modifier.padding(top = 30.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val nap = 20L
            listOf(
                Alarm(0, LocalTime.of(6, 0)) to if (twentyFour) "06:00" else "6:00 am",
                Alarm(0, LocalTime.of(7, 0)) to if (twentyFour) "07:00" else "7:00 am",
                Alarm(0, LocalTime.now().plusMinutes(nap).withSecond(0).withNano(0), label = stringResource(R.string.nap_minutes, nap)) to stringResource(R.string.nap_minutes, nap),
            ).forEach { (alarm, text) ->
                Text(
                    text,
                    style = sans(14, 500),
                    modifier = Modifier
                        .glass(RoundedCornerShape(20.dp), elevation = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onQuick(alarm) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun WarningCard(problem: RingProblem, onFix: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(Modifier.fillMaxWidth().glass(shape, elevation = 8.dp).padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(problem.text), style = sans(14, 500), modifier = Modifier.weight(1f).padding(end = 10.dp))
        Text(
            stringResource(R.string.fix),
            style = sans(14, 500, Color.White),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(PurpleRamp)
                .clickable(role = Role.Button, onClick = onFix)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** The Settings page that fixes [problem], for this app. */
private fun Context.openFix(problem: RingProblem) {
    val intent = when {
        problem == RingProblem.EXACT && Build.VERSION.SDK_INT >= 31 -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
        problem == RingProblem.FULL_SCREEN && Build.VERSION.SDK_INT >= 34 -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri())
        else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}


/** 12h / 24h, a small glass segmented switch; the chosen half dark. */
@Composable
private fun FormatSwitch(twentyFour: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.time_format)
    Row(
        Modifier
            .glass(RoundedCornerShape(14.dp), alpha = 0.55f, elevation = 4.dp)
            .padding(3.dp)
            .selectableGroup()
            .semantics { contentDescription = label },
    ) {
        listOf(false to "12h", true to "24h").forEach { (value, text) ->
            val on = value == twentyFour
            Text(
                text,
                style = smallText(12, if (on) Color.White else Ink.muted),
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Ink.chosen else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onChange(value) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** The next alarm: hours over minutes, light to dark, then a dotted line from now to when it rings. */
@Composable
private fun NextAlarmCard(alarm: Alarm, at: ZonedDateTime, now: ZonedDateTime, twentyFour: Boolean) {
    val until = Duration.between(now, at)
    val hour = if (twentyFour) at.hour else (at.hour + 11) % 12 + 1
    val shape = RoundedCornerShape(32.dp)
    Column(Modifier.fillMaxWidth().glass(shape, elevation = 16.dp).clip(shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(stringResource(R.string.next_alarm, alarm.label.ifBlank { stringResource(R.string.alarm) }), style = sans(13, 500), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.rings_in, spanText(until)), style = sans(13, 500, Ink.accent))
        }
        DigitRow(hour.toString().padStart(2, '0'), stringResource(R.string.unit_h), Ink.lilac, divider = true)
        DigitRow(at.minute.toString().padStart(2, '0'), stringResource(R.string.unit_min), Ink.text, divider = false)
        NightLine(now.hm(twentyFour), at.hm(twentyFour))
    }
}

@Composable
private fun DigitRow(value: String, unit: String, color: Color, divider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.drawBehind { drawLine(Ink.text.copy(alpha = 0.08f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) } else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Digits(value, HeroDigits.copy(color = color), 100.dp, Modifier.weight(1f))
        Text(unit, style = smallText(11), modifier = Modifier.padding(bottom = 14.dp))
    }
}

/** A dotted dip from now (left) to the alarm (right); the first stretch, already behind you, in purple. */
@Composable
private fun NightLine(nowLabel: String, endLabel: String) {
    Box(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 14.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, 18.dp.toPx())
                cubicTo(size.width * 0.23f, 64.dp.toPx(), size.width * 0.77f, 64.dp.toPx(), size.width, 18.dp.toPx())
            }
            drawPath(path, Ink.text.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 5.dp.toPx()))))
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * 0.2f, part)
            drawPath(part, Ink.purple, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val tip = measure.getPosition(measure.length * 0.2f)
            drawCircle(Ink.purple.copy(alpha = 0.2f), 11.dp.toPx(), tip)
            drawCircle(Ink.purple, 6.dp.toPx(), tip)
        }
        Text(stringResource(R.string.now_label, nowLabel), style = smallText(10))
        Text(endLabel, style = smallText(10), modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun AlarmTile(alarm: Alarm, hero: Boolean, twentyFour: Boolean, modifier: Modifier, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val fg = when {
        hero -> Color.White
        alarm.on -> Ink.text
        else -> Ink.soft
    }
    val time = alarm.time.format(if (twentyFour) HourMinute else HourMinute12)
    Column(
        modifier
            .height(128.dp)
            .then(
                if (hero) Modifier
                    .shadow(14.dp, shape, ambientColor = Ink.purpleDeep.copy(alpha = 0.3f), spotColor = Ink.purpleDeep.copy(alpha = 0.4f))
                    .background(PurpleRamp, shape)
                else Modifier.glass(shape, alpha = if (alarm.on) 0.6f else 0.3f, elevation = 8.dp),
            )
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.edit_alarm), onClick = onOpen)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val name = alarm.label.ifBlank { stringResource(R.string.alarm) }
            Text(name, style = sans(14, 500, fg), modifier = Modifier.weight(1f))
            PillSwitch(alarm.on, onToggle, name, track = if (hero) Color.White.copy(alpha = 0.3f) else Ink.purple)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = sans(38, 400, fg, spacing = -1f))
            if (!twentyFour) Text(if (alarm.time.hour < 12) "am" else "pm", style = sans(13, 500, fg.copy(alpha = 0.7f)), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Text(
            daysLabel(alarm),
            style = smallText(10, if (hero) Color.White else Ink.chipFg),
            modifier = Modifier
                .background(if (hero) Color.White.copy(alpha = 0.22f) else Ink.chipBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
