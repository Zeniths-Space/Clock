package com.elara.clock

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Settings, a full page over the app: time format, theme, alarm defaults (snooze length, how long an alarm
 * rings, gradual volume) and the home city. Every choice applies at once; Back or the back button closes it.
 */
@Composable
fun SettingsPage(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val s = Settings.get(context)
    val set = { change: (SettingsValues) -> SettingsValues -> Settings.update(context, change) }
    val cityMap = LocalCityMap.current
    Box(
        Modifier
            .fillMaxSize()
            .pageBackground()
            .pointerInput(Unit) {} // Takes every touch: the tabs below must not get them.
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Back, stringResource(R.string.back), size = 48.dp, iconSize = 20.dp, onClick = onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.settings), style = sans(24, 500), modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.settings_sub), style = sans(13, color = Ink.muted))
                }
                Spacer(Modifier.width(48.dp))
            }

            Label(stringResource(R.string.time_format))
            Segmented(
                listOf(stringResource(R.string.follow_phone), stringResource(R.string.hours_12), stringResource(R.string.hours_24)),
                TimeFormat.entries.indexOf(s.timeFormat),
            ) { i -> set { it.copy(timeFormat = TimeFormat.entries[i]) } }

            Label(stringResource(R.string.theme))
            Segmented(
                listOf(stringResource(R.string.follow_phone), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                ThemeMode.entries.indexOf(s.theme),
            ) { i -> set { it.copy(theme = ThemeMode.entries[i]) } }

            Label(stringResource(R.string.alarms_settings))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), elevation = 8.dp)) {
                ChoiceRow(stringResource(R.string.snooze_length), stringResource(R.string.snooze_setting, s.snoozeMinutes), Settings.SnoozeChoices, s.snoozeMinutes) { m ->
                    set { it.copy(snoozeMinutes = m) }
                }
                Divider()
                ChoiceRow(stringResource(R.string.ring_for), stringResource(R.string.ring_for_value, s.ringMinutes), Settings.RingChoices, s.ringMinutes) { m ->
                    set { it.copy(ringMinutes = m) }
                }
                Divider()
                SwitchRow(
                    stringResource(R.string.gradual_volume),
                    stringResource(if (s.gradualVolume) R.string.gradual_volume_on else R.string.gradual_volume_off),
                    s.gradualVolume,
                ) { on -> set { it.copy(gradualVolume = on) } }
            }

            Label(stringResource(R.string.home_city))
            val phoneCity = remember { Cities.phone(context) }
            val chosen = Cities.chosen(context)
            val homeLine = s.homeCity
            val home = Cities.home(context)
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), elevation = 8.dp).selectableGroup().padding(vertical = 6.dp)) {
                HomeRow(stringResource(R.string.home_automatic), stringResource(R.string.home_automatic_sub, phoneCity.name), homeLine == null) {
                    set { it.copy(homeCity = null) }
                }
                // Your added cities, and the chosen home city if it is none of them.
                val listed = (chosen + listOfNotNull(homeLine?.let(City::fromLine))).distinctBy { it.name + it.zone.id }
                listed.forEach { city ->
                    HomeRow(city.name, listOf(city.country, utcLabel(city.zone, java.time.Instant.now())).filter(String::isNotEmpty).joinToString(" · "), homeLine != null && city.same(home)) {
                        set { it.copy(homeCity = city.toLine()) }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { cityMap.pickHome(home) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_on_map), style = sans(16, 500, Ink.accent))
                        Text(stringResource(R.string.home_on_map_sub), style = sans(13, color = Ink.muted))
                    }
                    Icon(Icons.Globe, null, tint = Ink.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A segmented pill of equal parts, the chosen one filled purple with white text. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(32.dp), elevation = 6.dp)
            .padding(5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            Text(
                text,
                style = sans(15, 500, if (on) Color.White else Ink.text),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (on) Ink.purple else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) }
                    .padding(vertical = 13.dp),
            )
        }
    }
}

/** A setting with a row of minute choices under it. */
@Composable
private fun ChoiceRow(title: String, value: String, choices: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, style = sans(16, 500))
        Text(value, style = sans(13, color = Ink.accent))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { m ->
                val on = m == selected
                Text(
                    stringResource(R.string.minutes_short, m),
                    style = sans(13, 500, if (on) Color.White else Ink.text),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Ink.purple else if (Ink.dark) Color(0xFF1F1F1F) else Color(0xFFE6E2EF))
                        .selectable(on, role = Role.RadioButton) { onSelect(m) }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** One home-city choice; the chosen one filled purple at 22% with a check. */
@Composable
private fun HomeRow(title: String, sub: String, on: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Ink.purple.copy(alpha = 0.22f) else Color.Transparent)
            .selectable(on, role = Role.RadioButton, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(16, 500, if (on) Ink.accent else Ink.text))
            Text(sub, style = sans(13, color = Ink.muted))
        }
        if (on) Icon(Icons.Check, null, tint = Ink.accent, modifier = Modifier.size(20.dp))
    }
}
