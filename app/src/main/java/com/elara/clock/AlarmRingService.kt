package com.elara.clock

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Plays a ringing alarm's sound: its own choice or the default in Settings ([Tones]), which a notification
 * channel can't do (it fixes one sound), on the alarm volume, and with gradual volume from quiet to full over
 * [Settings.RampSeconds]. It runs only while an alarm rings, as a foreground service showing the alarm's own
 * notification, and stops on Snooze, Dismiss, or after the ring time in Settings. If it can't start or play
 * anything, the alarm rings through its notification with the phone's alarm sound.
 */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var ringing = -1
    private val handler = Handler(Looper.getMainLooper())
    private var started = 0L
    private var limitMillis = 0L
    private var ramp = false

    private val step = object : Runnable {
        override fun run() {
            val p = player ?: return
            val elapsed = System.currentTimeMillis() - started
            if (elapsed >= limitMillis) {
                AlarmEngine.timedOut(this@AlarmRingService, ringing)
                return
            }
            if (ramp) {
                // Quadratic, so the first seconds stay soft (loudness is roughly logarithmic in volume).
                val t = (elapsed / (Settings.RampSeconds * 1000f)).coerceIn(0f, 1f)
                val v = 0.02f + 0.98f * t * t
                p.setVolume(v, v)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(RingActivity.AlarmId, -1) ?: -1
        val notification = Pending.remove(id)
        if (id < 0 || notification == null) {
            // Restarted by Android with nothing to ring: never ring again on its own.
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1000 + id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(1000 + id, notification)
        }
        play(id)
        return START_NOT_STICKY
    }

    private fun play(id: Int) {
        stopPlayer()
        ringing = id
        started = System.currentTimeMillis()
        limitMillis = Settings.get(this).ringMinutes * 60_000L
        ramp = Settings.get(this).gradualVolume
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // The chosen sound, else the phone's: a chosen file may be gone, or unreadable before the first unlock.
        player = listOfNotNull(AlarmEngine.toneFor(this, id), fallback).distinct().firstNotNullOfOrNull { uri -> open(uri) }
        if (player == null) {
            // No sound could be played (a missing or broken alarm sound): ring the plain way instead.
            AlarmEngine.ringWithoutService(this, id)
            stopSelf()
            return
        }
        handler.post(step)
    }

    private fun open(uri: android.net.Uri): MediaPlayer? {
        val p = MediaPlayer()
        return runCatching {
            p.apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                setWakeMode(this@AlarmRingService, PowerManager.PARTIAL_WAKE_LOCK) // Keeps it playing (and rising) with the screen off.
                val v = if (ramp) 0.02f else 1f
                setVolume(v, v)
                prepare()
                start()
            }
        }.onFailure { p.release() }.getOrNull()
    }

    private fun stopPlayer() {
        handler.removeCallbacks(step)
        player?.run { runCatching { stop() }; release() }
        player = null
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }

    companion object {
        /** The notification for each alarm about to start ringing, handed over in this process. */
        private val Pending = java.util.concurrent.ConcurrentHashMap<Int, Notification>()

        /** Starts ringing alarm [id] with [notification] as its foreground notification; false if Android refused. */
        fun start(context: Context, id: Int, notification: Notification): Boolean {
            Pending[id] = notification
            return runCatching {
                context.startForegroundService(Intent(context, AlarmRingService::class.java).putExtra(RingActivity.AlarmId, id))
            }.isSuccess.also { if (!it) Pending.remove(id) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }
}
