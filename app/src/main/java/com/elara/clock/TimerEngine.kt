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
 * One countdown, [id] its own. [endsAt] is the [SystemClock.elapsedRealtime] it ends at while running, 0
 * otherwise; [leftMs] is what is left while paused. [paused] marks a timer that was started and is waiting to
 * go on.
 */
data class TimerState(
    val id: Int = 1,
    val minutes: Int = 10,
    val durationMs: Long = 10 * 60_000L,
    val leftMs: Long = 10 * 60_000L,
    val endsAt: Long = 0,
    val paused: Boolean = false,
) {
    val running get() = endsAt != 0L

    /** Neither running nor paused: only being set up. */
    val idle get() = !running && !paused

    fun left(now: Long = SystemClock.elapsedRealtime()) = if (running) (endsAt - now).coerceAtLeast(0) else leftMs
}

/** The timers and which one the screen shows. Timers being set up that aren't shown are dropped ([TimerEngine.prune]). */
data class Timers(val list: List<TimerState> = listOf(TimerState()), val selected: Int = 1) {
    val current get() = list.firstOrNull { it.id == selected } ?: list.first()

    /** "Timer" with one timer, else "Timer 2": its place in the list. */
    fun name(base: String, id: Int) = if (list.size <= 1) base else "$base ${list.indexOfFirst { it.id == id } + 1}"

    /** Only timers that run or wait paused, and the shown one; never none. */
    fun prune(): Timers {
        val kept = list.filter { !it.idle || it.id == selected }.ifEmpty { listOf(current) }
        return copy(list = kept, selected = if (kept.any { it.id == selected }) selected else kept.first().id)
    }

    /** A new timer of [minutes], shown; the shown one is kept only if it runs or is paused. */
    fun add(minutes: Int): Timers {
        val id = (list.maxOf { it.id }) + 1
        return Timers(list.filter { !it.idle } + TimerState(id, minutes, minutes * 60_000L, minutes * 60_000L), id)
    }

    fun change(id: Int, f: (TimerState) -> TimerState) = copy(list = list.map { if (it.id == id) f(it) else it })
}

/**
 * The timers outside the screen. Their state lives in device-protected storage, so they survive the app being
 * closed and the phone restarting. While one runs, an exact alarm is set for its end and its own ongoing
 * notification counts down by itself (Android draws the chronometer; no app code runs each second). When it
 * ends, it rings like an alarm: full screen, on the alarm volume, until stopped or the ring time in Settings
 * runs out. Each timer has its own alarm, notifications and ringing; timer 1 keeps the ids the single timer
 * used before, so an update finds its alarm and notification.
 */
object TimerEngine {
    private fun ongoingId(id: Int) = 900 + (id - 1) * 2
    private fun ringId(id: Int) = 901 + (id - 1) * 2
    private const val OngoingChannel = "timer"
    const val TimerId = "com.elara.clock.TIMER_ID"

    private val state = mutableStateOf<Timers?>(null)

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences("timer", Context.MODE_PRIVATE)

    private fun bootCount(context: Context) =
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)

    /** The timers, a Compose state so the screen follows the notifications' buttons. */
    fun timers(context: Context): Timers = state.value ?: prefs(context).let { p ->
        val rebooted = p.getInt("boot", -1) != bootCount(context)
        val saved = p.getString("timers", null)?.let { text ->
            runCatching {
                val a = org.json.JSONArray(text)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    TimerState(o.getInt("id"), o.getInt("minutes"), o.getLong("duration"), o.getLong("left"), o.getLong("ends_at"), o.optBoolean("paused")) to o.optLong("ends_wall")
                }
            }.getOrNull()
        } ?: listOf(
            // Saved by the single-timer version: becomes timer 1.
            TimerState(
                minutes = p.getInt("minutes", 10),
                durationMs = p.getLong("duration", 10 * 60_000L),
                leftMs = p.getLong("left", 10 * 60_000L),
                endsAt = p.getLong("ends_at", 0),
                paused = p.getBoolean("paused", false),
            ) to p.getLong("ends_wall", 0),
        )
        // elapsedRealtime starts again at 0 after a restart: move each end by the wall clock instead.
        val list = saved.map { (s, wall) ->
            if (s.running && rebooted) s.copy(endsAt = SystemClock.elapsedRealtime() + (wall - System.currentTimeMillis()).coerceAtLeast(1)) else s
        }.ifEmpty { listOf(TimerState()) }
        Timers(list, p.getInt("selected", list.first().id)).let { it.copy(selected = it.current.id) }
    }.also { state.value = it }

    /** The timer the screen shows. */
    fun get(context: Context): TimerState = timers(context).current

    private fun update(context: Context, change: (Timers) -> Timers) {
        val before = timers(context)
        val t = change(before)
        state.value = t
        // commit, not apply: a notification button's receiver may be killed right after it returns.
        prefs(context).edit(commit = true) {
            val a = org.json.JSONArray()
            t.list.forEach { s ->
                a.put(
                    org.json.JSONObject().put("id", s.id).put("minutes", s.minutes).put("duration", s.durationMs).put("left", s.leftMs)
                        .put("ends_at", s.endsAt).put("ends_wall", System.currentTimeMillis() + s.left()).put("paused", s.paused),
                )
            }
            putString("timers", a.toString())
            putInt("selected", t.selected)
            putInt("boot", bootCount(context))
        }
        // Timers taken out: their alarm and notification go too.
        before.list.filter { old -> t.list.none { it.id == old.id } }.forEach { schedule(context, t, it.copy(endsAt = 0, paused = false)) }
        t.list.forEach { schedule(context, t, it) }
    }

    private fun change(context: Context, id: Int, f: (TimerState) -> TimerState) = update(context) { it.change(id, f) }

    private fun shown(context: Context) = timers(context).selected

    fun start(context: Context, id: Int = shown(context)) = change(context, id) {
        if (it.running || it.leftMs <= 0) it else it.copy(endsAt = SystemClock.elapsedRealtime() + it.leftMs, paused = false)
    }

    fun pause(context: Context, id: Int = shown(context)) = change(context, id) { if (it.running) it.copy(leftMs = it.left(), endsAt = 0, paused = true) else it }

    fun reset(context: Context, id: Int = shown(context)) = change(context, id) { it.copy(leftMs = it.durationMs, endsAt = 0, paused = false) }

    fun choose(context: Context, minutes: Int) = change(context, shown(context)) { TimerState(it.id, minutes, minutes * 60_000L, minutes * 60_000L) }

    fun addMinute(context: Context, id: Int = shown(context)) = change(context, id) {
        if (it.running) it.copy(endsAt = it.endsAt + 60_000, durationMs = it.durationMs + 60_000)
        else it.copy(leftMs = it.leftMs + 60_000, durationMs = it.durationMs + 60_000)
    }

    /** Shows timer [id] on the screen; a timer only being set up that is left drops out. */
    fun select(context: Context, id: Int) = update(context) { it.copy(selected = id).prune() }

    /** A new timer, shown, starting at the shown one's preset. */
    fun add(context: Context) = update(context) { it.add(it.current.minutes) }

    /** Sets the end alarms again after a restart or an update: Android forgets them. */
    fun reschedule(context: Context) {
        val t = timers(context)
        t.list.forEach { schedule(context, t, it) }
    }

    /** Timer [id]'s end alarm went off: back to its full duration, and ring. */
    fun ring(context: Context, id: Int) {
        val timers = timers(context)
        val s = timers.list.firstOrNull { it.id == id } ?: return
        if (!s.running) return
        val title = timers.name(context.getString(R.string.tab_timer), id)
        change(context, id) { it.copy(leftMs = it.durationMs, endsAt = 0, paused = false) }
        val manager = notifications(context)
        AlarmEngine.createChannels(context, manager)
        val full = PendingIntent.getActivity(
            context, ringId(id),
            Intent(context, RingActivity::class.java).putExtra(RingActivity.Timer, true).putExtra(TimerId, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Counts up from the end, as "−00:12", so a late look shows how long ago it went off.
        val info = context.getString(R.string.timer_of, durationText(context, s.durationMs))
        val notification = Notification.Builder(context, AlarmEngine.RingChannel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(context.getString(R.string.timer_done))
            .setContentText(info)
            .setSubText(title)
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
            .addAction(button(context, R.string.stop, TimerReceiver.Stop, id))
            .addAction(button(context, R.string.restart, TimerReceiver.Restart, id))
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(ringId(id), notification)
    }

    /** Stops timer [id] ringing. */
    fun stop(context: Context, id: Int) = notifications(context).cancel(ringId(id))

    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun schedule(context: Context, timers: Timers, s: TimerState) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val ring = action(context, TimerReceiver.Ring, s.id)
        if (s.running) {
            if (AlarmEngine.canRingOnTime(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.endsAt, ring)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.endsAt, ring)
        } else {
            alarms.cancel(ring)
        }
        showOngoing(context, timers, s)
    }

    /** Running: a countdown Android updates by itself. Paused: the time left. Otherwise: nothing. */
    private fun showOngoing(context: Context, timers: Timers, s: TimerState) {
        val manager = notifications(context)
        if (s.idle) return manager.cancel(ongoingId(s.id))
        val title = timers.name(context.getString(R.string.tab_timer), s.id)
        // Low importance: shows without a sound or a pop-up.
        manager.createNotificationChannel(NotificationChannel(OngoingChannel, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, ongoingId(s.id),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenTimer, true).putExtra(TimerId, s.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
            .setContentTitle(title)
            .setContentText(info)
            .setSubText(title)
            // One group, so several timers stack in the shade.
            .setGroup(OngoingChannel)
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
            builder.addAction(button(context, R.string.pause, TimerReceiver.Pause, s.id))
            builder.addAction(button(context, R.string.add_minute_short, TimerReceiver.AddMinute, s.id))
        } else {
            builder.addAction(button(context, R.string.resume, TimerReceiver.Start, s.id))
        }
        builder.addAction(button(context, R.string.reset, TimerReceiver.Reset, s.id))
        manager.notify(ongoingId(s.id), builder.build())
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

    private fun button(context: Context, label: Int, action: String, id: Int) =
        Notification.Action.Builder(null, context.getString(label), action(context, action, id)).build()

    // Timer 1 keeps the single timer's request codes, so an update finds the alarm it set.
    private fun action(context: Context, action: String, id: Int) = PendingIntent.getBroadcast(
        context, action.hashCode() + id - 1, Intent(context, TimerReceiver::class.java).setAction(action).putExtra(TimerId, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The timer ending, and the buttons on its notifications. Not exported: only this app's PendingIntents reach it. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No id: an alarm the single-timer version set, for what is now timer 1.
        val id = intent.getIntExtra(TimerEngine.TimerId, 1)
        when (intent.action) {
            Ring -> TimerEngine.ring(context, id)
            Start -> TimerEngine.start(context, id)
            Pause -> TimerEngine.pause(context, id)
            Reset -> TimerEngine.reset(context, id)
            Stop -> TimerEngine.stop(context, id)
            AddMinute -> TimerEngine.addMinute(context, id)
            Restart -> { TimerEngine.stop(context, id); TimerEngine.start(context, id) }
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
