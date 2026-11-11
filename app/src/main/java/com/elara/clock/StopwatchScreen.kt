package com.elara.clock

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** "01:04.37": minutes, seconds and hundredths. */
fun lapText(ms: Long) = "%02d:%02d.%02d".format(ms / 60_000, ms / 1000 % 60, ms / 10 % 100)

/**
 * Minutes, seconds and hundredths in the stacked rows, laps in glass below. Its state lives in
 * [StopwatchEngine], so it keeps counting with the app closed. It redraws every frame only while it runs and
 * is on screen.
 */
@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val watch = StopwatchEngine.get(context)
    val laps = watch.laps
    val running = watch.running
    var frame by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Every frame while running and on screen; hidden behind another tab it keeps time without drawing.
    val shown = LocalTabShown.current
    LaunchedEffect(running, shown) {
        if (running && shown) frame = SystemClock.elapsedRealtime()
        while (running && shown) withFrameMillis { frame = SystemClock.elapsedRealtime() }
    }
    val ms = watch.elapsed(if (running) frame else SystemClock.elapsedRealtime())

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = NavBarSpace),
    ) {
        Text(stringResource(R.string.tab_stopwatch), style = sans(22, 500), modifier = Modifier.padding(horizontal = 20.dp))
        Column(
            Modifier
                .padding(top = 14.dp)
                .drawBehind { drawLine(Ink.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .semantics(mergeDescendants = true) { contentDescription = lapText(ms) },
        ) {
            StackRow((ms / 60_000).toString().padStart(2, '0'), stringResource(R.string.unit_min), Ink.faded)
            StackRow((ms / 1000 % 60).toString().padStart(2, '0'), stringResource(R.string.unit_sec), Ink.purpleLight)
            StackRow((ms / 10 % 100).toString().padStart(2, '0'), "1/100", Ink.text)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lap while running; reset once stopped.
            if (running || watch.idle) {
                GlassIconButton(Icons.Flag, stringResource(R.string.lap), size = 60.dp, iconSize = 22.dp) { StopwatchEngine.lap(context) }
            } else {
                GlassIconButton(Icons.Reset, stringResource(R.string.reset), size = 60.dp, iconSize = 22.dp) { StopwatchEngine.reset(context) }
            }
            MainButton(if (running) Icons.Pause else Icons.Play, stringResource(if (running) R.string.pause else R.string.start)) {
                if (running) {
                    StopwatchEngine.pause(context)
                } else {
                    frame = SystemClock.elapsedRealtime() // Else the first frame uses an old frame time and jumps.
                    StopwatchEngine.start(context)
                }
            }
        }

        if (laps.isNotEmpty()) {
            Text(stringResource(R.string.laps), style = smallText(11), modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp), elevation = 10.dp).padding(vertical = 6.dp),
            ) {
                laps.forEachIndexed { i, at ->
                    val previous = laps.getOrNull(i + 1) ?: 0L
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.lap_n, laps.size - i), style = sans(15, 500), modifier = Modifier.weight(1f))
                        Text(lapText(at - previous), style = smallText(13, Ink.accent, weight = 500), modifier = Modifier.padding(end = 16.dp))
                        Text(lapText(at), style = smallText(13, Ink.text))
                    }
                }
            }
        }
    }
}
