package com.elara.clock

import android.text.format.DateFormat
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** An alarm as the screen shows it. This first version keeps sample alarms in memory; nothing rings yet. */
data class Alarm(val label: String, val time: LocalTime, val days: Set<DayOfWeek>, val on: Boolean)

private val SampleAlarms = listOf(
    Alarm("Gym", LocalTime.of(6, 30), setOf(MONDAY, WEDNESDAY, FRIDAY), true),
    Alarm("Work", LocalTime.of(7, 15), setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), true),
    Alarm("Nap", LocalTime.of(14, 0), setOf(SATURDAY), false),
    Alarm("Medicine", LocalTime.of(21, 0), DayOfWeek.entries.toSet(), true),
)

/** When [alarm] next rings after [now], or null when it is off. */
fun nextRing(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
    if (!alarm.on) return null
    for (d in 0..7) {
        val day = now.toLocalDate().plusDays(d.toLong())
        val at = day.atTime(alarm.time).atZone(now.zone)
        if (at.isAfter(now) && (alarm.days.isEmpty() || day.dayOfWeek in alarm.days)) return at
    }
    return null
}

/** "MON WED FRI", "WEEKDAYS", "WEEKENDS", "EVERY DAY", or "ONCE". */
fun daysLabel(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> "ONCE"
    days.size == 7 -> "EVERY DAY"
    days == setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY) -> "WEEKDAYS"
    days == setOf(SATURDAY, SUNDAY) -> "WEEKENDS"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() }
}

private val HeroDigits = outfit(112, weight = 600, spacing = -6f)

@Composable
fun AlarmsScreen(modifier: Modifier = Modifier) {
    val now by rememberNow()
    val zone = remember { ZoneId.systemDefault() }
    var twentyFour by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val systemTwentyFour = DateFormat.is24HourFormat(LocalContext.current)
    val use24 = twentyFour ?: systemTwentyFour
    val alarms = remember { mutableStateListOf(*SampleAlarms.toTypedArray()) }
    val here = now.atZone(zone)
    val next = alarms.mapIndexedNotNull { i, a -> nextRing(a, here)?.let { i to it } }.minByOrNull { it.second }
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
                Text(stringResource(R.string.tab_alarms), style = outfit(30, 500, spacing = -0.5f), modifier = Modifier.weight(1f))
                FormatSwitch(use24) { twentyFour = it }
            }
            next?.let { (i, at) -> NextAlarmCard(alarms[i], at, here, use24) }
            alarms.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { col, alarm ->
                        val index = row * 2 + col
                        AlarmTile(alarm, hero = next?.first == index, use24, Modifier.weight(1f)) { alarms[index] = alarm.copy(on = it) }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        val addLabel = stringResource(R.string.add_alarm)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 104.dp)
                .size(60.dp)
                .shadow(14.dp, CircleShape, ambientColor = Ink.purpleDeep.copy(alpha = 0.35f), spotColor = Ink.purpleDeep.copy(alpha = 0.45f))
                .background(PurpleRamp, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = addLabel) {}
                .semantics { contentDescription = addLabel },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Plus, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
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
                style = mono(12, if (on) Color.White else Ink.muted),
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Ink.text else Color.Transparent)
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
            Text(stringResource(R.string.next_alarm, alarm.label), style = outfit(13, 500), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.rings_in, spanText(until)), style = outfit(13, 500, Ink.purpleDeep))
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
        Text(unit, style = mono(11), modifier = Modifier.padding(bottom = 14.dp))
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
        Text(stringResource(R.string.now_label, nowLabel), style = mono(10))
        Text(endLabel, style = mono(10), modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun AlarmTile(alarm: Alarm, hero: Boolean, twentyFour: Boolean, modifier: Modifier, onToggle: (Boolean) -> Unit) {
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
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(alarm.label, style = outfit(14, 500, fg), modifier = Modifier.weight(1f))
            PillSwitch(alarm.on, onToggle, alarm.label, track = if (hero) Color.White.copy(alpha = 0.3f) else Ink.purple)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = outfit(38, 400, fg, spacing = -1f))
            if (!twentyFour) Text(if (alarm.time.hour < 12) "am" else "pm", style = outfit(13, 500, fg.copy(alpha = 0.7f)), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Text(
            daysLabel(alarm.days),
            style = mono(10, if (hero) Color.White else Ink.night),
            modifier = Modifier
                .background(if (hero) Color.White.copy(alpha = 0.22f) else Ink.lavender, RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
