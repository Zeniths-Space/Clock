package com.elara.clock

import android.icu.text.TimeZoneNames
import android.icu.util.ULocale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One named time zone, such as Eastern Time: [zone] is its main zone (America/New_York), [place] that zone's
 * city. [name], [abbreviation] and [specific] ("Eastern Daylight Time") are for [at]; the last two may be
 * empty where the language has none.
 */
data class NamedZone(val name: String, val specific: String, val abbreviation: String, val place: String, val zone: ZoneId)

/**
 * Every named time zone in use at [at], from the phone's own time-zone names (ICU's CLDR "metazones"): one
 * entry each for Eastern Time, Pacific Time, India Standard Time and so on, named in [locale]. Zones the
 * phone's java.time does not know are left out.
 */
fun namedZones(at: Instant, locale: Locale): List<NamedZone> {
    val names = TimeZoneNames.getInstance(ULocale.forLocale(locale))
    // Abbreviations like EST and PST are only in US English's data; other English locales have none.
    val usNames = TimeZoneNames.getInstance(ULocale.US)
    val millis = at.toEpochMilli()
    return names.availableMetaZoneIDs.mapNotNull { mz ->
        val ref = names.getReferenceZoneID(mz, "001") ?: return@mapNotNull null
        if (names.getMetaZoneID(ref, millis) != mz) return@mapNotNull null // No longer used, as for former zones.
        val zone = runCatching { ZoneId.of(ref) }.getOrNull() ?: return@mapNotNull null
        val dst = zone.rules.isDaylightSavings(at)
        val generic = names.getMetaZoneDisplayName(mz, TimeZoneNames.NameType.LONG_GENERIC)
        val specific = names.getMetaZoneDisplayName(mz, if (dst) TimeZoneNames.NameType.LONG_DAYLIGHT else TimeZoneNames.NameType.LONG_STANDARD)
        val name = generic ?: specific ?: return@mapNotNull null
        val shortType = if (dst) TimeZoneNames.NameType.SHORT_DAYLIGHT else TimeZoneNames.NameType.SHORT_STANDARD
        val short = (names.getMetaZoneDisplayName(mz, shortType) ?: usNames.getMetaZoneDisplayName(mz, shortType).takeIf { locale.language == "en" }).orEmpty()
        NamedZone(
            name = name,
            specific = specific?.takeIf { it != name }.orEmpty(),
            abbreviation = short,
            place = names.getExemplarLocationName(ref).orEmpty(),
            zone = zone,
        )
    }.sortedWith(compareBy({ it.zone.rules.getOffset(at).totalSeconds }, { it.name }))
}

/**
 * All the world's time zones, a full page over the app: grouped by their offset from UTC, each group with
 * its time now (and Yesterday or Tomorrow when the date differs from yours), then each zone's name,
 * abbreviation and main city. Your own time is marked. A search narrows it by name, abbreviation, city or
 * offset ("pacific", "EST", "tokyo", "+5:45").
 */
@Composable
fun TimeZonesPage(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val now by rememberNow()
    val twentyFour = rememberTwentyFour()
    val home = remember(Settings.get(context).homeCity) { Cities.home(context) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] // Followed, so a language change renames the zones.
    // Worked out again when the minute changes, so a zone switching to or from daylight time is renamed.
    val zones by produceState(emptyList<NamedZone>(), now) { value = withContext(Dispatchers.Default) { namedZones(now, locale) } }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.fold()
    // A whole abbreviation ("est"), an offset ("+5:45"), or the start of a word in a name or city ("pac").
    val shown = if (q.isEmpty()) zones else zones.filter { z ->
        z.abbreviation.fold() == q || utcLabel(z.zone, now).fold().removePrefix("utc ").startsWith(q.removePrefix("utc ")) ||
            listOf(z.name, z.specific, z.place).any { field -> field.fold().split(' ', '-', '/').any { it.startsWith(q) } || field.fold().startsWith(q) }
    }
    val groups = shown.groupBy { it.zone.rules.getOffset(now).totalSeconds }
    val homeOffset = home.zone.rules.getOffset(now).totalSeconds
    val homeDate = now.atZone(home.zone).toLocalDate()

    Box(
        Modifier
            .fillMaxSize()
            .pageBackground()
            .pointerInput(Unit) {} // Takes every touch: the tabs below must not get them.
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Back, stringResource(R.string.back), size = 48.dp, iconSize = 20.dp, onClick = onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.time_zones), style = sans(24, 500), modifier = Modifier.semantics { heading() })
                    if (zones.isNotEmpty()) Text(pluralStringResource(R.plurals.time_zones_count, zones.size, zones.size), style = sans(13, color = Ink.muted))
                }
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(12.dp))
            ZoneSearch(query) { query = it.take(40) }
            // Opens on your own time, with the group before it still in view.
            val list = androidx.compose.foundation.lazy.rememberLazyListState()
            androidx.compose.runtime.LaunchedEffect(zones.isNotEmpty()) {
                val i = groups.keys.indexOf(homeOffset)
                if (zones.isNotEmpty() && q.isEmpty() && i > 0) list.scrollToItem(i - 1)
            }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list) {
            if (zones.isNotEmpty() && groups.isEmpty()) {
                item { Text(stringResource(R.string.no_zone_found), style = sans(15, color = Ink.muted), modifier = Modifier.padding(vertical = 24.dp, horizontal = 6.dp)) }
            }
            groups.forEach { (offset, list) ->
                item(key = offset) {
                    val there = now.atZone(list.first().zone)
                    val mine = offset == homeOffset
                    val day = there.toLocalDate().compareTo(homeDate).let {
                        if (it < 0) stringResource(R.string.yesterday) else if (it > 0) stringResource(R.string.tomorrow) else ""
                    }
                    Column(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(26.dp), elevation = 8.dp)
                            .then(if (mine) Modifier.background(Ink.purple.copy(alpha = 0.14f), RoundedCornerShape(26.dp)) else Modifier)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(utcLabel(list.first().zone, now), style = smallText(12, Ink.accent, weight = 500))
                                Text(
                                    listOfNotNull(day.ifEmpty { null }, if (mine) stringResource(R.string.your_time) else null).joinToString(" · "),
                                    style = sans(12, color = Ink.muted),
                                )
                            }
                            Text(there.hm(twentyFour), style = sans(38, 300, spacing = -1f))
                            if (!twentyFour) Text(if (there.hour < 12) "am" else "pm", style = sans(13, 500, Ink.muted), modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                        }
                        list.forEach { z ->
                            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(Ink.line))
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(z.name, style = sans(16, 500))
                                Text(
                                    listOf(z.abbreviation.takeUnless { it.startsWith("GMT") }.orEmpty(), z.specific, z.place).filter(String::isNotEmpty).joinToString(" · "),
                                    style = sans(13, color = Ink.muted),
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
        }
    }
}

/** Lower case without accents, so "sao" finds São Paulo. */
private fun String.fold() = Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('−', '-')

@Composable
private fun ZoneSearch(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val hint = stringResource(R.string.search_zones)
    Row(
        Modifier.fillMaxWidth().glass(shape, elevation = 8.dp).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Search, null, tint = Ink.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = sans(16, color = Ink.muted))
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = sans(16),
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
