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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

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
    LaunchedEffect(asked) {
        if (asked != null) {
            tab = asked
            onAskedShown()
        }
    }
    // Back from any other tab returns to the clock first, as in the stock app.
    BackHandler(enabled = tab != Tab.CLOCK) { tab = Tab.CLOCK }
    Box(Modifier.fillMaxSize().pageBackground()) {
        Crossfade(tab, animationSpec = tween(160), label = "tab", modifier = Modifier.statusBarsPadding()) { shown ->
            when (shown) {
                Tab.CLOCK -> ClockScreen()
                Tab.WORLD -> WorldScreen()
                Tab.ALARMS -> AlarmsScreen()
                Tab.TIMER -> TimerScreen()
                Tab.STOPWATCH -> StopwatchScreen()
            }
        }
        NavBar(
            tab,
            onSelect = { tab = it },
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
        )
    }
}
