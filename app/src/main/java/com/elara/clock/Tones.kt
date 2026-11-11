package com.elara.clock

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.IntentCompat

/**
 * Alarm sounds. An alarm's sound is a content URI chosen in Android's sound picker; null means "the default":
 * for an alarm, the sound in Settings; for Settings, the phone's alarm sound.
 */
object Tones {
    /** The sound for an alarm whose own choice is [tone]: it, else Settings', else the phone's. */
    fun forAlarm(context: Context, tone: String?): Uri? =
        (tone ?: Settings.get(context).alarmTone)?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    /** The sound's name as the phone shows it; null if it can't be read (a removed file, say). Reads a provider: not on the main thread. */
    fun title(context: Context, uri: Uri?): String? = runCatching {
        // The "default" URI names itself "Default (…)": name the sound it stands for instead.
        val actual = if (uri == null || uri == SystemSettings.System.DEFAULT_ALARM_ALERT_URI) {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        } else uri
        RingtoneManager.getRingtone(context, actual)?.getTitle(context)
    }.getOrNull()
}

/**
 * Opens Android's alarm sound picker on [current] (null: its Default entry, which plays [default]) and gives
 * back the choice; picking the alarm Default gives null. Nothing is given back when it is closed without a
 * choice. Samsung's picker shows ringtones, not alarm sounds, and no alarm Default (see onPicked).
 */
@Composable
fun rememberTonePicker(onPicked: (String?) -> Unit): (current: String?, default: Uri?) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        // Only the alarm default means "the default". Samsung's picker lists ringtones whatever the type, and its
        // "Default ringtone" entry gives the ringtone default: kept as picked, so the alarm rings what was chosen.
        onPicked(picked?.takeIf { it != SystemSettings.System.DEFAULT_ALARM_ALERT_URI }?.toString())
    }
    return { current, default ->
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, default ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let(Uri::parse) ?: SystemSettings.System.DEFAULT_ALARM_ALERT_URI),
        )
    }
}
