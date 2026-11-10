package com.elara.clock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Content drawn over the whole app (tabs and tab bar) in the activity's own window. Sheets and dialogs from
 * Compose's material library each open a new window, which cost 70-100 ms per open at 60 Hz on the S24 Ultra.
 */
class OverlayHost {
    private val entries = mutableStateListOf<State<@Composable () -> Unit>>()

    fun add(entry: State<@Composable () -> Unit>) { entries += entry }
    fun remove(entry: State<@Composable () -> Unit>) { entries -= entry }

    /** Draws every entry, the latest on top. */
    @Composable
    fun Show() = entries.forEach { key(it) { it.value() } }
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

/** Shows [content] over the whole app while this is in composition; where there is no host, in place. */
@Composable
fun Overlay(content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current ?: return content()
    val latest = rememberUpdatedState(content)
    DisposableEffect(host) {
        host.add(latest)
        onDispose { host.remove(latest) }
    }
}

/**
 * The Elara bottom sheet, drawn in the app's window: a dimmed backdrop that closes it when tapped, and the
 * sheet sliding up from the bottom with 32 dp top corners. It never drags (as the Elara forms require), Back
 * and a tap outside call [onDismissRequest], and it rises above the keyboard.
 */
@Composable
fun ElaraSheet(onDismissRequest: () -> Unit, containerColor: Color, content: @Composable ColumnScope.() -> Unit) = Overlay {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    BackHandler(onBack = onDismissRequest)
    val closeLabel = stringResource(R.string.close)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                .semantics { onClick(closeLabel) { onDismissRequest(); true } },
        )
        // Above the keyboard and never under the status bar; the form takes at most 90% of what is left.
        Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    // Read at draw time, so sliding in never recomposes the form.
                    .graphicsLayer { translationY = (1f - shown.value) * size.height }
                    .clip(shape)
                    .background(containerColor, shape)
                    .pointerInput(Unit) {}, // Touches on the sheet never reach the backdrop.
                content = content,
            )
        }
    }
}
