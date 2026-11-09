package com.elara.clock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * The current time, updated at each new minute and only while the app is on screen: no screen shows
 * seconds, so nothing redraws more often than once a minute, and nothing runs in the background.
 */
@Composable
fun rememberNow(): State<Instant> {
    val owner = LocalLifecycleOwner.current
    return produceState(Instant.now(), owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }
}

enum class Tab(val icon: ImageVector, val label: Int) {
    CLOCK(Icons.Clock, R.string.tab_clock),
    WORLD(Icons.Globe, R.string.tab_world),
    ALARMS(Icons.Alarm, R.string.tab_alarms),
    TIMER(Icons.Hourglass, R.string.tab_timer),
    STOPWATCH(Icons.Stopwatch, R.string.tab_stopwatch),
}

/** The floating glass tab bar: icons only, the open tab in a dark pill. Names are read out, not shown. */
@Composable
fun NavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(33.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .glass(shape, alpha = 0.72f, elevation = 14.dp)
            .padding(horizontal = 9.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            val fill by animateColorAsState(if (on) Ink.text else Color.Transparent, label = "tab")
            val tint by animateColorAsState(if (on) Color.White else Ink.text, label = "tab icon")
            val width by animateDpAsState(if (on) 56.dp else 48.dp, label = "tab width")
            Box(
                Modifier
                    .size(width, 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(fill)
                    .selectable(selected = on, role = Role.Tab) { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, stringResource(tab.label), tint = tint, modifier = Modifier.size(21.dp))
            }
        }
    }
}

/** A round glass button with an icon, 44 dp. */
@Composable
fun GlassIconButton(icon: ImageVector, label: String, size: Dp = 44.dp, iconSize: Dp = 18.dp, onClick: () -> Unit = {}) {
    Box(
        Modifier
            .size(size)
            .glass(CircleShape, alpha = 0.55f, elevation = 6.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Ink.text, modifier = Modifier.size(iconSize)) }
}

/** The design's switch: a pill with a white knob, purple when on. [track] overrides the on colour. */
@Composable
fun PillSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, track: Color = Ink.purple) {
    val bg by animateColorAsState(if (checked) track else Ink.text.copy(alpha = 0.12f), label = "switch")
    val knob by animateDpAsState(if (checked) 16.dp else 0.dp, label = "knob")
    Box(
        Modifier
            .size(40.dp, 24.dp)
            .clip(CircleShape)
            .background(bg)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label }
            .padding(3.dp),
    ) {
        Box(Modifier.offset { IntOffset(knob.roundToPx(), 0) }.size(18.dp).background(Color.White, CircleShape))
    }
}

/** Space the floating tab bar takes at the bottom of each screen, so content can scroll clear of it. */
val NavBarSpace = 66.dp + 22.dp + 24.dp
