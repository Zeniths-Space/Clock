package com.elara.clock

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Adding a city, in the Elara form sheet: a search field over every city the app knows, each with its time
 * zone and the time there now. Tapping one adds it and closes the sheet; cities you already have are ticked
 * and can't be added twice. Nothing typed here is worth asking about, so closing never asks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val catalog by produceState<List<City>?>(null) { value = withContext(Dispatchers.IO) { Cities.catalog(context) } }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = Cities.chosen(context)
    val now by rememberNow()
    val twentyFour = DateFormat.is24HourFormat(context)
    val results = remember(catalog, query) {
        val q = query.fold()
        catalog.orEmpty().filter { q.isEmpty() || it.name.fold().contains(q) || it.country.fold().contains(q) || it.zone.id.fold().contains(q) }
            // Names that start with what you typed first.
            .sortedBy { if (it.name.fold().startsWith(q)) 0 else 1 }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = if (Ink.dark) Color.Black else Ink.ground,
    ) {
        Column(Modifier.fillMaxHeight(0.9f).navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            Header(
                stringResource(R.string.add_city),
                if (catalog == null) "" else pluralStringResource(R.plurals.cities_known, catalog!!.size, catalog!!.size),
                onClose = onClose,
                icon = Icons.Globe,
            )
            Spacer(Modifier.height(18.dp))
            SearchField(query) { query = it.take(40) }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.name + it.zone.id }) { city ->
                    val added = chosen.any { it.same(city) }
                    CityResult(city, added, now.atZone(city.zone).hm(twentyFour) + if (twentyFour) "" else if (now.atZone(city.zone).hour < 12) " am" else " pm") {
                        Cities.add(context, city)
                        onClose()
                    }
                }
                if (catalog != null && results.isEmpty()) {
                    item { Text(stringResource(R.string.no_city_found), style = sans(15, color = Ink.muted), modifier = Modifier.padding(vertical = 24.dp, horizontal = 6.dp)) }
                }
            }
        }
    }
}

/** Lower case without accents, so "sao" finds São Paulo. */
private fun String.fold() = Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val hint = stringResource(R.string.search_cities)
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

@Composable
private fun CityResult(city: City, added: Boolean, time: String, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = !added, role = Role.Button, onClick = onAdd)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(Cookie).background(Ink.purple.copy(alpha = if (added) 0.22f else 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Text(city.code, style = sans(12, 500, Ink.accent)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(city.name, style = sans(16, 500))
            Text(listOf(city.country, utcLabel(city.zone, java.time.Instant.now())).filter(String::isNotEmpty).joinToString(" · "), style = sans(12, color = Ink.muted))
        }
        if (added) {
            Text(stringResource(R.string.added), style = sans(13, 500, Ink.accent))
        } else {
            Text(time, style = sans(15, color = Ink.text))
        }
    }
}
