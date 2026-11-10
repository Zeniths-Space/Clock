package com.elara.clock

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

enum class TimeFormat { PHONE, H12, H24 }
enum class ThemeMode { PHONE, LIGHT, DARK }

/**
 * The app's settings. [homeCity] is a city line (see [City.toLine]), or null to follow the phone's time zone.
 * [gradualVolume] makes an alarm start quietly and rise to full volume over [RampSeconds].
 */
data class SettingsValues(
    val timeFormat: TimeFormat = TimeFormat.PHONE,
    val theme: ThemeMode = ThemeMode.PHONE,
    val snoozeMinutes: Int = 10,
    val ringMinutes: Int = 10,
    val gradualVolume: Boolean = false,
    val homeCity: String? = null,
)

/**
 * The settings, in device-protected storage like the alarms, so an alarm ringing before the first unlock
 * after a restart still uses your snooze and ring lengths. A Compose state, so screens follow changes.
 */
object Settings {
    val SnoozeChoices = listOf(5, 10, 15, 20)
    val RingChoices = listOf(1, 5, 10, 15, 20, 30)
    const val RampSeconds = 60

    private val state = mutableStateOf<SettingsValues?>(null)

    private fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun get(context: Context): SettingsValues = state.value ?: prefs(context).let { p ->
        SettingsValues(
            timeFormat = p.getString("time_format", null)?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() } ?: TimeFormat.PHONE,
            theme = p.getString("theme", null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.PHONE,
            snoozeMinutes = p.getInt("snooze_minutes", 10).takeIf { it in SnoozeChoices } ?: 10,
            ringMinutes = p.getInt("ring_minutes", 10).takeIf { it in RingChoices } ?: 10,
            gradualVolume = p.getBoolean("gradual_volume", false),
            homeCity = p.getString("home_city", null)?.takeIf { City.fromLine(it) != null },
        )
    }.also { state.value = it }

    fun update(context: Context, change: (SettingsValues) -> SettingsValues) {
        val v = change(get(context))
        state.value = v
        prefs(context).edit {
            putString("time_format", v.timeFormat.name)
            putString("theme", v.theme.name)
            putInt("snooze_minutes", v.snoozeMinutes)
            putInt("ring_minutes", v.ringMinutes)
            putBoolean("gradual_volume", v.gradualVolume)
            putString("home_city", v.homeCity)
        }
    }

    /** Whether times show as 24-hour: the setting, or the phone's own choice. */
    fun twentyFour(context: Context) = when (get(context).timeFormat) {
        TimeFormat.PHONE -> DateFormat.is24HourFormat(context)
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }
}

/** [Settings.twentyFour], followed by Compose when the setting changes. */
@Composable
fun rememberTwentyFour(): Boolean = Settings.twentyFour(LocalContext.current)

/** Whether the app is dark: the setting, or the phone's theme. */
@Composable
fun appIsDark(): Boolean = when (Settings.get(LocalContext.current).theme) {
    ThemeMode.PHONE -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
