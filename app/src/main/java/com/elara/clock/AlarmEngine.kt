package com.elara.clock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.ZonedDateTime

/**
 * The alarms and everything that makes them ring. Alarms live in device-protected storage, so the receivers
 * below can read them and set them again after a reboot even before the phone is first unlocked (an alarm set
 * for 6:30 still rings if the phone restarted overnight). Each alarm that is on is set with
 * [AlarmManager.setAlarmClock]: exact, allowed in Doze, and shown as the alarm icon in the status bar. When it
 * rings, a full-screen notification on the alarm volume repeats until it is snoozed or dismissed. Nothing runs
 * between alarms.
 */
object AlarmEngine {
    private const val FileName = "alarms.json"
    const val SnoozeMinutes = 10L
    /** Stops ringing after this long, as alarm clocks do, so a forgotten phone does not ring for hours. */
    private const val RingMillis = 10 * 60_000L
    /** One channel per sound and vibration choice: Android fixes both per channel. */
    private enum class Channel(val id: String, val sound: Boolean, val vibrate: Boolean, val label: Int) {
        BOTH("alarm", true, true, R.string.channel_alarms),
        SOUND("alarm_no_vibration", true, false, R.string.channel_alarms_quiet),
        VIBRATE("alarm_vibrate_only", false, true, R.string.channel_alarms_vibrate),
        SILENT("alarm_silent", false, false, R.string.channel_alarms_silent),
    }

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    private var loaded = false

    private fun file(context: Context) = AtomicFile(File(context.createDeviceProtectedStorageContext().filesDir, FileName))

    /** The alarms, loaded on first use. */
    fun alarms(context: Context): StateFlow<List<Alarm>> {
        load(context)
        return _alarms
    }

    // shortcut: read and written on the calling thread (often the main thread); the file is a few KB at most.
    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        _alarms.value = runCatching { Alarm.listFromJson(String(file(context).readFully())) }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun update(context: Context, change: (List<Alarm>) -> List<Alarm>) {
        load(context)
        val list = change(_alarms.value).sortedBy { it.time }
        _alarms.value = list
        // Written before returning, not on a background thread: a receiver's process may be killed right after it
        // returns, and a lost write could make a one-time alarm ring again the next day. The file is a few KB.
        val f = file(context)
        val out = f.startWrite()
        try {
            out.write(Alarm.listToJson(list).toByteArray())
            f.finishWrite(out)
        } catch (e: java.io.IOException) {
            f.failWrite(out)
        }
        schedule(context, list)
    }

    fun newId(context: Context): Int = (alarms(context).value.maxOfOrNull { it.id } ?: 0) + 1

    /** Adds [alarm], or replaces the one with its id. Saving clears a pending snooze. */
    fun save(context: Context, alarm: Alarm) = update(context) { list -> list.filter { it.id != alarm.id } + alarm.copy(snoozedUntil = 0) }

    fun delete(context: Context, id: Int) {
        notifications(context).cancel(notificationId(id))
        // Scheduling only covers alarms still in the list, so a deleted one's wake-up is cancelled here.
        context.getSystemService(AlarmManager::class.java).cancel(ringIntent(context, id))
        update(context) { list -> list.filter { it.id != id } }
    }

    fun setOn(context: Context, id: Int, on: Boolean) = update(context) { list -> list.map { if (it.id == id) it.copy(on = on, snoozedUntil = 0) else it } }

    /** Sets every alarm again: after a reboot, an app update, or a time or time-zone change. */
    fun reschedule(context: Context) {
        load(context)
        schedule(context, _alarms.value)
    }

    /** The next alarm to ring, with its time; null when none is on. */
    fun next(list: List<Alarm>, now: ZonedDateTime = ZonedDateTime.now()): Pair<Alarm, ZonedDateTime>? =
        list.mapNotNull { a -> nextRing(a, now)?.let { a to it } }.minByOrNull { it.second }

    /** Whether alarms ring exactly on time (Android 12 asks the person to allow it; 13+ grants alarm apps). */
    fun canRingOnTime(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Whether alarms can make a sound at all: they ring through a notification. */
    fun canNotify(context: Context) = notifications(context).areNotificationsEnabled()

    /** Whether alarms can take over the screen (Android 14+ can withdraw this). */
    fun canShowFullScreen(context: Context) = Build.VERSION.SDK_INT < 34 || notifications(context).canUseFullScreenIntent()

    private fun schedule(context: Context, list: List<Alarm>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        for (alarm in list) {
            val pending = ringIntent(context, alarm.id)
            val at = nextRing(alarm, now)
            if (at == null) {
                manager.cancel(pending)
                continue
            }
            val millis = at.toInstant().toEpochMilli()
            if (canRingOnTime(context)) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, openApp(context)), pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        }
    }

    /** The alarm went off: ring, then turn a one-time alarm off or leave a repeating one set for its next day. */
    fun ring(context: Context, id: Int) {
        load(context)
        val alarm = _alarms.value.firstOrNull { it.id == id } ?: return
        if (!alarm.on) return
        show(context, alarm)
        update(context) { list -> list.map { if (it.id == id) it.copy(on = it.days.isNotEmpty(), snoozedUntil = 0) else it } }
    }

    fun snooze(context: Context, id: Int) {
        notifications(context).cancel(notificationId(id))
        val until = System.currentTimeMillis() + SnoozeMinutes * 60_000
        update(context) { list -> list.map { if (it.id == id) it.copy(on = true, snoozedUntil = until) else it } }
    }

    fun dismiss(context: Context, id: Int) {
        notifications(context).cancel(notificationId(id))
        update(context) { list -> list.map { if (it.id == id && it.snoozedUntil != 0L) it.copy(snoozedUntil = 0, on = it.days.isNotEmpty()) else it } }
    }

    private fun notificationId(id: Int) = 1000 + id

    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun show(context: Context, alarm: Alarm) {
        val manager = notifications(context)
        createChannels(context, manager)
        val full = PendingIntent.getActivity(
            context, alarm.id,
            Intent(context, RingActivity::class.java).putExtra(RingActivity.AlarmId, alarm.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = alarm.time.format(HourMinute)
        val channel = Channel.entries.first { it.sound == alarm.sound && it.vibrate == alarm.vibrate }
        val builder = Notification.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(alarm.label.ifBlank { context.getString(R.string.alarm) })
            .setContentText(time)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setShowWhen(false)
            .setTimeoutAfter(RingMillis)
        if (alarm.snooze) builder.addAction(Notification.Action.Builder(null, context.getString(R.string.snooze), action(context, alarm.id, AlarmReceiver.Snooze)).build())
        val notification = builder
            .addAction(Notification.Action.Builder(null, context.getString(R.string.dismiss), action(context, alarm.id, AlarmReceiver.Dismiss)).build())
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT // The sound repeats until it is answered.
        manager.notify(notificationId(alarm.id), notification)
    }

    private fun ringIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, id, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.Ring).putExtra(RingActivity.AlarmId, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, id: Int, action: String) = PendingIntent.getBroadcast(
        context, id, Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(RingActivity.AlarmId, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Opens the app on its Alarms tab: the status bar's alarm icon leads here. */
    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenAlarms, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels(context: Context, manager: NotificationManager) {
        // On the alarm volume and usage, so it rings with the ringer on silent and through Do not disturb's alarm exception.
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        for (c in Channel.entries) {
            manager.createNotificationChannel(
                NotificationChannel(c.id, context.getString(c.label), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(if (c.sound) sound else null, attributes)
                    enableVibration(c.vibrate)
                    if (c.vibrate) vibrationPattern = longArrayOf(0, 600, 400, 600)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }
}

/** An alarm going off, and Snooze and Dismiss from its notification. Not exported: only this app's PendingIntents reach it. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(RingActivity.AlarmId, -1).takeIf { it >= 0 } ?: return
        when (intent.action) {
            Ring -> AlarmEngine.ring(context, id)
            Snooze -> AlarmEngine.snooze(context, id)
            Dismiss -> AlarmEngine.dismiss(context, id)
        }
    }

    companion object {
        const val Ring = "com.elara.clock.RING"
        const val Snooze = "com.elara.clock.SNOOZE"
        const val Dismiss = "com.elara.clock.DISMISS"
    }
}

/**
 * Sets the alarms again when Android forgets them (a reboot, also before the first unlock, or an app update) and
 * when wall-clock times move (the time or the time zone changed). Only these system broadcasts reach it.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> AlarmEngine.reschedule(context)
        }
    }
}
