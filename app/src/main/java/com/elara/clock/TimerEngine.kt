package com.elara.clock

import android.app.AlarmManager
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
 * counts down by itself (Android draws the chronometer; no app code runs each second). When it ends, it rings
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
        // Counts up from the end, as "−00:12", so a late look shows how long ago it went off.
        val info = context.getString(R.string.timer_of, durationText(context, s.durationMs))
        val notification = Notification.Builder(context, AlarmEngine.RingChannel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(context.getString(R.string.timer_done))
            .setContentText(info)
            .setSubText(context.getString(R.string.timer_done))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(timeView(context, R.layout.notification_timer, s.endsAt, countDown = false, info))
            .setCustomBigContentView(timeView(context, R.layout.notification_timer_big, s.endsAt, countDown = false, info))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setShowWhen(false)
            .setTimeoutAfter(Settings.get(context).ringMinutes * 60_000L)
            .addAction(button(context, R.string.stop, TimerReceiver.Stop))
            .addAction(button(context, R.string.restart, TimerReceiver.Restart))
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
        // Low importance: shows without a sound or a pop-up.
        manager.createNotificationChannel(NotificationChannel(OngoingChannel, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, OngoingId,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenTimer, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val left = s.left()
        val info = if (s.running) {
            context.getString(R.string.timer_ends_at, java.time.ZonedDateTime.now().plusNanos(left * 1_000_000).hm(Settings.twentyFour(context)))
        } else {
            context.getString(R.string.timer_paused)
        } + " · " + context.getString(R.string.timer_of, durationText(context, s.durationMs))
        // Running: Android counts the Chronometer down by itself. Paused: the time left as plain text.
        val end = if (s.running) s.endsAt else null
        val builder = Notification.Builder(context, OngoingChannel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(context.getString(R.string.tab_timer))
            .setContentText(info)
            .setSubText(context.getString(R.string.tab_timer))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(timeView(context, R.layout.notification_timer, end, countDown = true, info, left))
            .setCustomBigContentView(timeView(context, R.layout.notification_timer_big, end, countDown = true, info, left))
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        if (s.running) {
            builder.addAction(button(context, R.string.pause, TimerReceiver.Pause))
            builder.addAction(button(context, R.string.add_minute_short, TimerReceiver.AddMinute))
        } else {
            builder.addAction(button(context, R.string.resume, TimerReceiver.Start))
        }
        builder.addAction(button(context, R.string.reset, TimerReceiver.Reset))
        manager.notify(OngoingId, builder.build())
    }

    /**
     * The big time: a Chronometer from [base] (an elapsedRealtime) counting down to it or up from it, or,
     * with no [base], [staticMs] as plain muted text.
     */
    private fun timeView(context: Context, layout: Int, base: Long?, countDown: Boolean, info: String, staticMs: Long = 0) =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.info, info)
            if (base != null) {
                setChronometer(R.id.time, base, if (countDown) null else "−%s", true)
                setChronometerCountDown(R.id.time, countDown)
            } else {
                setViewVisibility(R.id.time, View.GONE)
                setViewVisibility(R.id.time_static, View.VISIBLE)
                setTextViewText(R.id.time_static, DateUtils.formatElapsedTime((staticMs + 999) / 1000))
            }
        }

    /** "10 min", "1 h 30 min", "45 sec". */
    private fun durationText(context: Context, ms: Long): String {
        val sec = ms / 1000
        val parts = listOfNotNull(
            (sec / 3600).takeIf { it > 0 }?.let { "$it ${context.getString(R.string.unit_h)}" },
            (sec / 60 % 60).takeIf { it > 0 }?.let { "$it ${context.getString(R.string.unit_min)}" },
            (sec % 60).takeIf { it > 0 || sec == 0L }?.let { "$it ${context.getString(R.string.unit_sec)}" },
        )
        return parts.joinToString(" ")
    }

    private fun button(context: Context, label: Int, action: String) =
        Notification.Action.Builder(null, context.getString(label), action(context, action)).build()

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
            AddMinute -> TimerEngine.addMinute(context)
            Restart -> { TimerEngine.stop(context); TimerEngine.start(context) }
        }
    }

    companion object {
        const val Ring = "com.elara.clock.TIMER_RING"
        const val Start = "com.elara.clock.TIMER_START"
        const val Pause = "com.elara.clock.TIMER_PAUSE"
        const val Reset = "com.elara.clock.TIMER_RESET"
        const val Stop = "com.elara.clock.TIMER_STOP"
        const val AddMinute = "com.elara.clock.TIMER_ADD_MINUTE"
        const val Restart = "com.elara.clock.TIMER_RESTART"
    }
}
