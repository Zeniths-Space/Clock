package com.elara.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/** Sunday first, as Nepali calendars set out the week. */
private val Week = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)

/** The Elara "cookie": a circle with 8 soft lobes, behind the sheet's header icon. */
internal val Cookie = GenericShape { size, _ ->
    val steps = 120
    for (i in 0..steps) {
        val a = i * 2 * Math.PI / steps
        val r = min(size.width, size.height) / 2 * (0.95f + 0.05f * cos(8 * a).toFloat())
        val x = size.width / 2 + r * kotlin.math.sin(a).toFloat()
        val y = size.height / 2 - r * cos(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * Adding or editing an alarm, laid out like Samsung's alarm editor inside the Elara form sheet: a wheel
 * time picker at the top, then the next ring date, the days, the name, and Sound, Vibration and Snooze,
 * with Cancel and Save at the bottom. The sheet does not drag; it closes from Cancel, its close button or a
 * tap outside, and asks before dropping changes. [editing] null adds a new alarm.
 */
@Composable
fun AlarmSheet(editing: Alarm?, twentyFour: Boolean, onSave: (Alarm) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val start = remember { editing ?: Alarm(id = 0, time = LocalTime.now().plusHours(1).withMinute(0)) }
    var hour by rememberSaveable { mutableIntStateOf(start.time.hour) }
    var minute by rememberSaveable { mutableIntStateOf(start.time.minute) }
    var days by rememberSaveable { mutableStateOf(start.days.map { it.value }) }
    var label by rememberSaveable { mutableStateOf(start.label) }
    var sound by rememberSaveable { mutableStateOf(start.sound) }
    var vibrate by rememberSaveable { mutableStateOf(start.vibrate) }
    var snooze by rememberSaveable { mutableStateOf(start.snooze) }
    var asking by rememberSaveable { mutableStateOf(false) }
    val draft = start.copy(
        time = LocalTime.of(hour, minute), days = days.mapTo(HashSet()) { DayOfWeek.of(it) }, label = label.trim(),
        sound = sound, vibrate = vibrate, snooze = snooze, on = true, snoozedUntil = 0,
    )
    // Closing asks first when an edit changed something, or a new alarm has a name typed (as the Elara forms do).
    val changed = if (editing == null) label.isNotBlank() else draft != editing.copy(on = true, snoozedUntil = 0)
    val close = { if (changed) asking = true else onClose() }

    val now by rememberNow()
    val at = nextRing(draft, now.atZone(java.time.ZoneId.systemDefault()))
    ElaraSheet(onDismissRequest = close, containerColor = if (Ink.dark) Color.Black else Ink.ground) {
        Column(Modifier.fillMaxHeight(0.9f).navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Header(
                    stringResource(if (editing == null) R.string.new_alarm else R.string.edit_alarm),
                    at?.let { stringResource(R.string.rings_in, spanText(Duration.between(now.atZone(it.zone), it).plusSeconds(59))) }.orEmpty(),
                    onClose = close,
                )
                TimeWheels(hour, minute, twentyFour) { h, m -> hour = h; minute = m }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), elevation = 10.dp).padding(18.dp)) {
                    Text(whenText(draft, at), style = sans(14, 500, Ink.accent))
                    Spacer(Modifier.height(14.dp))
                    DayPicker(days.toSet()) { d -> days = if (d in days) days - d else days + d }
                }
                Label(stringResource(R.string.alarm_name))
                NameField(label) { label = it.take(40) }
                Label(stringResource(R.string.sound))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), elevation = 8.dp)) {
                    SwitchRow(stringResource(R.string.sound), stringResource(if (sound) R.string.sound_default else R.string.sound_off), sound) { sound = it }
                    Divider()
                    SwitchRow(stringResource(R.string.vibration), stringResource(if (vibrate) R.string.vibration_on else R.string.off), vibrate) { vibrate = it }
                    Divider()
                    SwitchRow(
                        stringResource(R.string.snooze),
                        if (snooze) stringResource(R.string.snooze_setting, AlarmEngine.SnoozeMinutes) else stringResource(R.string.off),
                        snooze,
                    ) { snooze = it }
                }
                if (editing != null) {
                    Text(
                        stringResource(R.string.delete_alarm),
                        style = sans(15, 500, Danger),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(role = Role.Button, onClick = onDelete)
                            .padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.cancel), primary = false, Modifier.weight(1f), onClick = close)
                PillButton(stringResource(R.string.save), primary = true, Modifier.weight(1f)) { onSave(draft) }
            }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = if (Ink.dark) Color(0xFF1F1F1F) else Color.White,
            title = { Text(stringResource(R.string.discard_title), style = sans(20, 500)) },
            text = { Text(stringResource(R.string.discard_text), style = sans(15, color = Ink.muted)) },
            confirmButton = { TextButton(onClick = { asking = false; onClose() }) { Text(stringResource(R.string.discard), style = sans(15, 500, Danger)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.keep_editing), style = sans(15, 500, Ink.accent)) } },
        )
    }
}

private val Danger get() = if (Ink.dark) Color(0xFFFF8A8A) else Color(0xFFD93A3A)

@Composable
internal fun Header(title: String, subtitle: String, onClose: () -> Unit, icon: ImageVector = Icons.Alarm) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(Cookie).background(Ink.purple.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ink.accent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = sans(24, 500), modifier = Modifier.semantics { heading() })
            if (subtitle.isNotEmpty()) Text(subtitle, style = sans(13, color = Ink.muted))
        }
        GlassIconButton(Icons.Close, stringResource(R.string.close), size = 52.dp, iconSize = 20.dp, onClick = onClose)
    }
}

/** "Today · Mon, 9 Nov", "Tomorrow · Tue, 10 Nov", or for a repeating alarm "Weekdays · next Tue, 10 Nov". */
@Composable
private fun whenText(alarm: Alarm, at: ZonedDateTime?): String {
    if (at == null) return ""
    val date = at.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    val today = java.time.LocalDate.now(at.zone)
    return when {
        alarm.days.isNotEmpty() -> stringResource(R.string.every_days, daysLabel(alarm.days).lowercase().replaceFirstChar { it.uppercase() }, date)
        at.toLocalDate() == today -> stringResource(R.string.today_on, date)
        else -> stringResource(R.string.tomorrow_on, date)
    }
}

/** Hours, minutes and (12-hour clocks) AM/PM as scrolling wheels; the middle row is the chosen one. */
@Composable
private fun TimeWheels(hour: Int, minute: Int, twentyFour: Boolean, onChange: (Int, Int) -> Unit) {
    val pm = hour >= 12
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (twentyFour) {
            Wheel(24, hour, looping = true, label = { "%02d".format(it) }, name = stringResource(R.string.hour), width = 96.dp) { onChange(it, minute) }
        } else {
            Wheel(12, (hour + 11) % 12, looping = true, label = { (it + 1).toString() }, name = stringResource(R.string.hour), width = 96.dp) {
                onChange((it + 1) % 12 + if (pm) 12 else 0, minute)
            }
        }
        Text(":", style = sans(48, 300), modifier = Modifier.padding(horizontal = 4.dp))
        Wheel(60, minute, looping = true, label = { "%02d".format(it) }, name = stringResource(R.string.minute), width = 96.dp) { onChange(hour, it) }
        if (!twentyFour) {
            Spacer(Modifier.width(12.dp))
            Wheel(2, if (pm) 1 else 0, looping = false, label = { if (it == 0) "am" else "pm" }, name = stringResource(R.string.am_pm), width = 72.dp, small = true) {
                onChange(hour % 12 + if (it == 1) 12 else 0, minute)
            }
        }
    }
}

private val WheelRow = 64.dp

/**
 * A wheel of [count] values, three rows tall, snapping to the middle row. Rows fade and shrink with their
 * distance from the middle, read in the draw phase so scrolling does not recompose them. [looping] repeats
 * the values so the wheel turns past the end. Screen readers get the value and Up one / Down one actions.
 */
@Composable
private fun Wheel(count: Int, selected: Int, looping: Boolean, label: (Int) -> String, name: String, width: Dp, small: Boolean = false, onSelect: (Int) -> Unit) {
    val total = if (looping) count * 400 else count
    val base = if (looping) count * 200 else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + selected)
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }
    val up = stringResource(R.string.increase)
    val down = stringResource(R.string.decrease)
    // The latest callback: it closes over the other wheels' current values.
    val select by rememberUpdatedState(onSelect)
    // The centred row, once the wheel settles.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            val centred = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            select(centred % count)
        }
    }
    // Follows a value changed elsewhere (the AM/PM wheel moving the hour).
    LaunchedEffect(selected) {
        val centred = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
        if (centred % count != selected && !state.isScrollInProgress) {
            val target = centred - centred % count + selected
            state.scrollToItem(target.coerceIn(0, total - 1))
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = WheelRow),
        modifier = Modifier
            .width(width)
            .height(WheelRow * 3)
            .semantics {
                contentDescription = "$name ${label(selected)}"
                customActions = listOf(
                    CustomAccessibilityAction(up) { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 1).coerceAtLeast(0)) }; true },
                    CustomAccessibilityAction(down) { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex + 1).coerceAtMost(total - 1)) }; true },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(total) { i ->
            Box(
                Modifier
                    .height(WheelRow)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val d = abs(i - state.firstVisibleItemIndex - state.firstVisibleItemScrollOffset / rowPx)
                        val t = d.coerceAtMost(1f)
                        alpha = 1f - 0.72f * t
                        val s = 1f - 0.28f * t
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(i % count), style = if (small) sans(30, 400) else sans(54, 300, spacing = -1f))
            }
        }
    }
}

/** Seven day circles, Sunday first; chosen ones filled purple. None chosen rings once. */
@Composable
private fun DayPicker(chosen: Set<Int>, onToggle: (Int) -> Unit) {
    // Read from the configuration, so a language change redraws the day names.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Week.forEach { day ->
            val on = day.value in chosen
            val name = day.getDisplayName(TextStyle.FULL, locale)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (on) Ink.purple else if (Ink.dark) Color(0xFF1F1F1F) else Color.White.copy(alpha = 0.75f))
                    .toggleable(on, role = Role.Checkbox) { onToggle(day.value) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    style = sans(14, 500, if (on) Color.White else if (day == DayOfWeek.SUNDAY || day == DayOfWeek.SATURDAY) Ink.accent else Ink.text),
                )
            }
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = sans(15, 500, Ink.accent), modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val hint = stringResource(R.string.alarm_name)
    Box(Modifier.fillMaxWidth().glass(shape, elevation = 8.dp).padding(horizontal = 20.dp, vertical = 18.dp)) {
        if (value.isEmpty()) Text(hint, style = sans(16, color = Ink.muted))
        BasicTextField(
            value, onChange,
            singleLine = true,
            textStyle = sans(16),
            cursorBrush = SolidColor(Ink.purple),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
        )
    }
}

@Composable
private fun SwitchRow(title: String, value: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(16, 500))
            Text(value, style = sans(13, color = if (checked) Ink.accent else Ink.muted))
        }
        PillSwitch(checked, onChange, title)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Ink.line))
}

@Composable
internal fun PillButton(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .height(56.dp)
            .then(if (primary) Modifier.background(PurpleRamp, shape) else Modifier.glass(shape, elevation = 6.dp))
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(17, 500, if (primary) Color.White else Ink.text)) }
}
