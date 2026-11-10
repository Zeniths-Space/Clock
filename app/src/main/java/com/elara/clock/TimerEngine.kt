package com.elara.clock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * One countdown. [endsAt] is the [SystemClock.elapsedRealtime] it ends at while running, 0 otherwise; [leftMs]
 * is what is left while paused. [paused] marks a timer that was started and is waiting to go on.
 */
data class TimerState(
    val minutes: Int = 10,
    val durationMs: Long = 10 * 60_000L,
    val leftMs: Long = 10 * 60_000L,
    val endsAt: Long = 0,
    val paused: Boolean = false,
) {
    val running get() = endsAt != 0L

    fun left(now: Long = SystemClock.elapsedRealtime()) = if (running) (endsAt - now).coerceAtLeast(0) else leftMs
}

/**
 * The timer outside the screen. Its state lives in device-protected storage, so it survives the app being
 * closed and the phone restarting. While it runs, an exact alarm is set for its end and an ongoing notification
 * counts down by itself (Android draws the chronometer; no app code runs each second). On Android 16 QPR1+ the
 * notification asks to be a Live Update, which One UI shows as a chip in the status bar. When it ends, it rings
 * like an alarm: full screen, on the alarm volume, until stopped or the ring time in Settings runs out.
 */
object TimerEngine {
    private const val OngoingId = 900
    private const val RingId = 901
    private const val OngoingChannel = "timer"

    private val state = mutableStateOf<TimerState?>(null)

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences("timer", Context.MODE_PRIVATE)

    private fun bootCount(context: Context) =
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)

    /** The timer, a Compose state so the screen follows the notification's buttons. */
    fun get(context: Context): TimerState = state.value ?: prefs(context).let { p ->
        val s = TimerState(
            minutes = p.getInt("minutes", 10),
            durationMs = p.getLong("duration", 10 * 60_000L),
            leftMs = p.getLong("left", 10 * 60_000L),
            endsAt = p.getLong("ends_at", 0),
            paused = p.getBoolean("paused", false),
        )
        // elapsedRealtime starts again at 0 after a restart: move the end by the wall clock instead.
        if (s.running && p.getInt("boot", -1) != bootCount(context)) {
            s.copy(endsAt = SystemClock.elapsedRealtime() + (p.getLong("ends_wall", 0) - System.currentTimeMillis()).coerceAtLeast(1))
        } else s
    }.also { state.value = it }

    fun update(context: Context, change: (TimerState) -> TimerState) {
        val s = change(get(context))
        state.value = s
        // commit, not apply: a notification button's receiver may be killed right after it returns.
        prefs(context).edit(commit = true) {
            putInt("minutes", s.minutes)
            putLong("duration", s.durationMs)
            putLong("left", s.leftMs)
            putLong("ends_at", s.endsAt)
            putLong("ends_wall", System.currentTimeMillis() + s.left())
            putInt("boot", bootCount(context))
            putBoolean("paused", s.paused)
        }
        schedule(context, s)
    }

    fun start(context: Context) = update(context) {
        if (it.running || it.leftMs <= 0) it else it.copy(endsAt = SystemClock.elapsedRealtime() + it.leftMs, paused = false)
    }

    fun pause(context: Context) = update(context) { if (it.running) it.copy(leftMs = it.left(), endsAt = 0, paused = true) else it }

    fun reset(context: Context) = update(context) { it.copy(leftMs = it.durationMs, endsAt = 0, paused = false) }

    fun choose(context: Context, minutes: Int) = update(context) { TimerState(minutes, minutes * 60_000L, minutes * 60_000L) }

    fun addMinute(context: Context) = update(context) {
        if (it.running) it.copy(endsAt = it.endsAt + 60_000, durationMs = it.durationMs + 60_000)
        else it.copy(leftMs = it.leftMs + 60_000, durationMs = it.durationMs + 60_000)
    }

    /** Sets the end alarm again after a restart or an update: Android forgets it. */
    fun reschedule(context: Context) = schedule(context, get(context))

    /** The end alarm went off: back to the full duration, and ring. */
    fun ring(context: Context) {
        val s = get(context)
        if (!s.running) return
        update(context) { it.copy(leftMs = it.durationMs, endsAt = 0, paused = false) }
        val manager = notifications(context)
        AlarmEngine.createChannels(context, manager)
        val full = PendingIntent.getActivity(
            context, RingId,
            Intent(context, RingActivity::class.java).putExtra(RingActivity.Timer, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, AlarmEngine.RingChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(context.getString(R.string.timer_done))
            .setContentText(clockText(s.durationMs / 1000))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setShowWhen(false)
            .setTimeoutAfter(Settings.get(context).ringMinutes * 60_000L)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.stop), action(context, TimerReceiver.Stop)).build())
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(RingId, notification)
    }

    /** Stops the ringing. */
    fun stop(context: Context) = notifications(context).cancel(RingId)

    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun schedule(context: Context, s: TimerState) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val ring = action(context, TimerReceiver.Ring)
        if (s.running) {
            if (AlarmEngine.canRingOnTime(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.endsAt, ring)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.endsAt, ring)
        } else {
            alarms.cancel(ring)
        }
        showOngoing(context, s)
    }

    /** Running: a countdown Android updates by itself. Paused: the time left. Otherwise: nothing. */
    private fun showOngoing(context: Context, s: TimerState) {
        val manager = notifications(context)
        if (!s.running && !s.paused) return manager.cancel(OngoingId)
        // Low importance: shows without a sound or a pop-up. Not MIN, which a Live Update may not use.
        manager.createNotificationChannel(NotificationChannel(OngoingChannel, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, OngoingId,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenTimer, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, OngoingChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(context.getString(R.string.tab_timer))
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (s.running) {
            builder
                .setWhen(System.currentTimeMillis() + s.left())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentText(context.getString(R.string.timer_ends_at, java.time.ZonedDateTime.now().plusNanos(s.left() * 1_000_000).hm(Settings.twentyFour(context))))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.pause), action(context, TimerReceiver.Pause)).build())
            if (Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                builder.setRequestPromotedOngoing(true)
            }
        } else {
            builder
                .setShowWhen(false)
                .setContentText(context.getString(R.string.timer_paused, clockText((s.leftMs + 999) / 1000)))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.resume), action(context, TimerReceiver.Start)).build())
        }
        builder.addAction(Notification.Action.Builder(null, context.getString(R.string.reset), action(context, TimerReceiver.Reset)).build())
        manager.notify(OngoingId, builder.build())
    }

    private fun action(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The timer ending, and the buttons on its notifications. Not exported: only this app's PendingIntents reach it. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Ring -> TimerEngine.ring(context)
            Start -> TimerEngine.start(context)
            Pause -> TimerEngine.pause(context)
            Reset -> TimerEngine.reset(context)
            Stop -> TimerEngine.stop(context)
        }
    }

    companion object {
        const val Ring = "com.elara.clock.TIMER_RING"
        const val Start = "com.elara.clock.TIMER_START"
        const val Pause = "com.elara.clock.TIMER_PAUSE"
        const val Reset = "com.elara.clock.TIMER_RESET"
        const val Stop = "com.elara.clock.TIMER_STOP"
    }
}
