package com.elara.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * The stopwatch. [startedAt] is the [SystemClock.elapsedRealtime] it would have started at had it never
 * paused, while running, else 0; [heldMs] is the time run while paused. [laps] are total times at each lap,
 * newest first.
 */
data class StopwatchState(
    val startedAt: Long = 0,
    val heldMs: Long = 0,
    val laps: List<Long> = emptyList(),
) {
    val running get() = startedAt != 0L

    fun elapsed(now: Long = SystemClock.elapsedRealtime()) = if (running) (now - startedAt).coerceAtLeast(0) else heldMs

    /** Neither running nor holding a time: nothing to show outside the app. */
    val idle get() = !running && heldMs == 0L
}

/**
 * The stopwatch outside the screen, as the timer is ([TimerEngine]): its state lives in device-protected
 * storage, so it keeps counting with the app closed and across a restart. While it runs or holds a time, an
 * ongoing notification shows it (Android counts the Chronometer up by itself; no app code runs each second),
 * with Pause or Resume, Lap and Reset.
 */
object StopwatchEngine {
    private const val OngoingId = 950
    private const val Channel = "stopwatch"

    private val state = mutableStateOf<StopwatchState?>(null)

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    private fun bootCount(context: Context) =
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)

    /** The stopwatch, a Compose state so the screen follows the notification's buttons. */
    fun get(context: Context): StopwatchState = state.value ?: prefs(context).let { p ->
        val s = StopwatchState(
            startedAt = p.getLong("started_at", 0),
            heldMs = p.getLong("held", 0),
            laps = p.getString("laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() },
        )
        // elapsedRealtime starts again at 0 after a restart: place the start by the wall clock instead.
        if (s.running && p.getInt("boot", -1) != bootCount(context)) {
            val ran = (System.currentTimeMillis() - p.getLong("started_wall", System.currentTimeMillis())).coerceAtLeast(0)
            s.copy(startedAt = (SystemClock.elapsedRealtime() - ran).coerceAtLeast(1))
        } else s
    }.also { state.value = it }

    private fun update(context: Context, change: (StopwatchState) -> StopwatchState) {
        val s = change(get(context))
        state.value = s
        // commit, not apply: a notification button's receiver may be killed right after it returns.
        prefs(context).edit(commit = true) {
            putLong("started_at", s.startedAt)
            putLong("started_wall", System.currentTimeMillis() - s.elapsed())
            putInt("boot", bootCount(context))
            putLong("held", s.heldMs)
            putString("laps", s.laps.joinToString(","))
        }
        showOngoing(context, s)
    }

    fun start(context: Context) = update(context) {
        if (it.running) it else it.copy(startedAt = (SystemClock.elapsedRealtime() - it.heldMs).coerceAtLeast(1))
    }

    fun pause(context: Context) = update(context) { if (it.running) it.copy(heldMs = it.elapsed(), startedAt = 0) else it }

    fun lap(context: Context) = update(context) { if (it.running) it.copy(laps = listOf(it.elapsed()) + it.laps) else it }

    fun reset(context: Context) = update(context) { StopwatchState() }

    /** Shows the notification again after a restart or an update, which clear it. */
    fun refresh(context: Context) {
        val s = get(context)
        if (!s.idle) showOngoing(context, s)
    }

    private fun showOngoing(context: Context, s: StopwatchState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (s.idle) return manager.cancel(OngoingId)
        // Low importance: shows without a sound or a pop-up.
        manager.createNotificationChannel(NotificationChannel(Channel, context.getString(R.string.channel_stopwatch), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, OngoingId,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenStopwatch, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val state = context.getString(if (s.running) R.string.stopwatch_running else R.string.timer_paused)
        val info = s.laps.firstOrNull()?.let { last ->
            state + " · " + context.getString(R.string.lap_n, s.laps.size) + " " + lapText(last - (s.laps.getOrNull(1) ?: 0L))
        } ?: state
        val builder = Notification.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(context.getString(R.string.tab_stopwatch))
            .setContentText(info)
            .setSubText(context.getString(R.string.tab_stopwatch))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(timeView(context, R.layout.notification_timer, s, info))
            .setCustomBigContentView(timeView(context, R.layout.notification_timer_big, s, info))
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        if (s.running) {
            builder.addAction(button(context, R.string.pause, StopwatchReceiver.Pause))
            builder.addAction(button(context, R.string.lap, StopwatchReceiver.Lap))
        } else {
            builder.addAction(button(context, R.string.resume, StopwatchReceiver.Start))
        }
        builder.addAction(button(context, R.string.reset, StopwatchReceiver.Reset))
        manager.notify(OngoingId, builder.build())
    }

    /** Running: a Chronometer counting up from the start. Paused: the time held, as plain text. */
    private fun timeView(context: Context, layout: Int, s: StopwatchState, info: String) =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.info, info)
            if (s.running) {
                setChronometer(R.id.time, s.startedAt, null, true)
            } else {
                setViewVisibility(R.id.time, View.GONE)
                setViewVisibility(R.id.time_static, View.VISIBLE)
                setTextViewText(R.id.time_static, DateUtils.formatElapsedTime(s.heldMs / 1000))
            }
        }

    private fun button(context: Context, label: Int, action: String) =
        Notification.Action.Builder(
            null, context.getString(label),
            PendingIntent.getBroadcast(
                context, action.hashCode(), Intent(context, StopwatchReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
}

/** The stopwatch notification's buttons. Not exported: only this app's PendingIntents reach it. */
class StopwatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Start -> StopwatchEngine.start(context)
            Pause -> StopwatchEngine.pause(context)
            Lap -> StopwatchEngine.lap(context)
            Reset -> StopwatchEngine.reset(context)
        }
    }

    companion object {
        const val Start = "com.elara.clock.STOPWATCH_START"
        const val Pause = "com.elara.clock.STOPWATCH_PAUSE"
        const val Lap = "com.elara.clock.STOPWATCH_LAP"
        const val Reset = "com.elara.clock.STOPWATCH_RESET"
    }
}
