package com.elara.clock

import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    /** A tab asked for from outside (the status bar's alarm icon opens Alarms); null once shown. */
    private var asked by mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holdSplash()
        // Status and navigation bar icons dark on the light page, light on black; nothing painted behind them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        if (savedInstanceState == null) asked = tabFor(intent)
        setContent { ElaraTheme { ClockApp(asked) { asked = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        asked = tabFor(intent)
    }

    private fun tabFor(intent: Intent?) = if (intent?.getBooleanExtra(OpenAlarms, false) == true) Tab.ALARMS else null

    companion object {
        const val OpenAlarms = "com.elara.clock.OPEN_ALARMS"
    }
}

/**
 * Android removes its splash at the app's first frame, which here comes before the hands have finished
 * sweeping. Keep it until they settle (plus a beat), then fade it (Android 12+; older versions show a still
 * splash as the window background).
 */
private fun ComponentActivity.holdSplash() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    splashScreen.setOnExitAnimationListener { view ->
        // The start is on the uptime clock (Android stores SystemClock.uptimeMillis() in it).
        val start = view.iconAnimationStart?.toEpochMilli() ?: SystemClock.uptimeMillis()
        val settled = start + (view.iconAnimationDuration?.toMillis() ?: 0L)
        val wait = (settled - SystemClock.uptimeMillis()).coerceIn(0L, 1000L) + 150L
        view.animate().alpha(0f).setStartDelay(wait).setDuration(250).withEndAction { view.remove() }.start()
    }
}

@Composable
fun ClockApp(asked: Tab? = null, onAskedShown: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.CLOCK) }
    val cityMap = remember { CityMapState() }
    val overlay = remember { OverlayHost() }
    LaunchedEffect(asked) {
        if (asked != null) {
            tab = asked
            onAskedShown()
        }
    }
    // Back from any other tab returns to the clock first, as in the stock app.
    BackHandler(enabled = tab != Tab.CLOCK) { tab = Tab.CLOCK }
    Box(Modifier.fillMaxSize().pageBackground()) {
        // Every tab stays composed once opened, and the others are built once the app is idle: switching then
        // only fades, instead of building a whole screen in the first frame (a 30-50 ms frame at 60 Hz), and a
        // running stopwatch or timer keeps its time when you look at another tab.
        val opened = remember { mutableStateListOf(tab) }
        if (tab !in opened) opened += tab
        LaunchedEffect(Unit) {
            delay(1200)
            Tab.entries.forEach { if (it !in opened) { opened += it; delay(150) } }
        }
        Box(Modifier.statusBarsPadding()) {
            CompositionLocalProvider(LocalCityMap provides cityMap, LocalOverlayHost provides overlay) { TabStack(tab, opened) }
        }
        NavBar(
            tab,
            onSelect = { tab = it },
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
        )
        overlay.Show()
        if (cityMap.open) CityMap(cityMap.focus, onClose = cityMap::close)
    }
}

/** Whether the tab around this content is the one on screen; hidden tabs stay composed but stop per-frame work. */
val LocalTabShown = compositionLocalOf { true }

/**
 * One tab's screen in the stack. Every tab stays placed, so its drawing is kept and showing it again only
 * changes the alpha, read here in placement so a fade never recomposes. At alpha 0 Android skips drawing
 * it; while fading it goes through a kept layer, so the fade does not draw every card, shadow and big digit
 * again each frame. The tab on screen sits on top and takes every touch (its empty pointerInput stops the
 * touch reaching hidden tabs below); hidden tabs are also left out for TalkBack.
 */
private fun Modifier.tabLayer(shown: Boolean, alpha: () -> Float) = this
    .then(if (shown) Modifier.pointerInput(Unit) {} else Modifier.clearAndSetSemantics {})
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, p.height) {
            p.placeWithLayer(0, 0, zIndex = if (shown) 1f else 0f) {
                val a = alpha()
                this.alpha = a
                compositingStrategy = if (a > 0f && a < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
        }
    }

/** Every opened tab, stacked; see [tabLayer]. */
@Composable
private fun TabStack(tab: Tab, opened: List<Tab>) {
    Box {
        Tab.entries.forEach { t ->
            if (t in opened) key(t) {
                val shown = t == tab
                val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "tab")
                Box(Modifier.fillMaxSize().tabLayer(shown) { alpha }) {
                    CompositionLocalProvider(LocalTabShown provides shown) {
                        when (t) {
                            Tab.CLOCK -> ClockScreen()
                            Tab.WORLD -> WorldScreen()
                            Tab.ALARMS -> AlarmsScreen()
                            Tab.TIMER -> TimerScreen()
                            Tab.STOPWATCH -> StopwatchScreen()
                        }
                    }
                }
            }
        }
    }
}
