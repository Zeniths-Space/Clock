package com.elara.clock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.ZoneId

/**
 * The alarm ringing, over the lock screen: the time in the stacked digits, the alarm's name, and Snooze and
 * Dismiss. Started by the ringing notification's full-screen intent; the notification makes the sound, so
 * leaving this without a choice leaves the alarm ringing in the notification shade. Always AMOLED dark: it
 * mostly shows in a dark room.
 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AlarmId, -1)
        val alarm = AlarmEngine.alarms(this).value.firstOrNull { it.id == id } ?: return finish()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            ElaraTheme(dark = true) {
                // Back does not silence it by accident: answer with a button.
                BackHandler {}
                RingScreen(
                    alarm,
                    onSnooze = { AlarmEngine.snooze(this, alarm.id); finish() },
                    onDismiss = { AlarmEngine.dismiss(this, alarm.id); finish() },
                )
            }
        }
    }

    companion object {
        const val AlarmId = "com.elara.clock.ALARM_ID"
    }
}

@Composable
private fun RingScreen(alarm: Alarm, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val now by rememberNow()
    val here = now.atZone(ZoneId.systemDefault())
    val title = alarm.label.ifBlank { stringResource(R.string.alarm) }
    Column(
        Modifier
            .fillMaxSize()
            .pageBackground()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = sans(30, 500), textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.ringing), style = smallText(12, Ink.accent), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp))
        val twentyFour = rememberTwentyFour()
        val time = here.hm(twentyFour)
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = time }, horizontalAlignment = Alignment.CenterHorizontally) {
            Digits(here.format(if (twentyFour) H else H12), sans(150, 200, Ink.lilac, spacing = -8f), 130.dp)
            Digits(here.format(M), sans(150, 200, spacing = -8f), 130.dp)
        }
        Spacer(Modifier.weight(1.3f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (alarm.snooze) RingButton(stringResource(R.string.snooze_for, Settings.get(LocalContext.current).snoozeMinutes), primary = false, Modifier.weight(1f), onSnooze)
            RingButton(stringResource(R.string.dismiss), primary = true, Modifier.weight(1f), onDismiss)
        }
    }
}

private val H = java.time.format.DateTimeFormatter.ofPattern("HH")
private val H12 = java.time.format.DateTimeFormatter.ofPattern("hh")
private val M = java.time.format.DateTimeFormatter.ofPattern("mm")

@Composable
private fun RingButton(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier
            .height(68.dp)
            .then(if (primary) Modifier.background(PurpleRamp, shape) else Modifier.glass(shape))
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(18, 500, if (primary) Color.White else Ink.text)) }
}
