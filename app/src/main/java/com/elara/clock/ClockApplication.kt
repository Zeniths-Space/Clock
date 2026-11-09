package com.elara.clock

import android.app.Application

/**
 * Sets every alarm again whenever the app's process starts, for any reason. Android forgets alarms when the
 * app is updated and relies on the MY_PACKAGE_REPLACED broadcast to have them set again, but One UI sometimes
 * skips or fails to deliver it (seen on SM-S928B, 2026-11-09, installs over adb). Opening the app, an alarm
 * or any broadcast then puts them back. Cheap: one small file read and a few AlarmManager calls.
 */
class ClockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmEngine.reschedule(this)
    }
}
