# Architecture

One app module, Jetpack Compose, no other libraries.

- `MainActivity.kt`: the activity, splash hand-off and `ClockApp`, which stacks the five tabs, floats the
  tab bar over them and draws the city map over both. Every tab stays composed and placed once opened
  (the rest are built when the app is idle), so a running stopwatch or timer keeps going on another tab
  and a switch only fades: hidden tabs sit at alpha 0, which Android skips drawing, and a fading tab goes
  through a kept layer. `LocalTabShown` tells a tab whether it is on screen, so per-frame work stops.
- `Theme.kt`: the colours (`Ink`, light and AMOLED dark), text styles in the system font, the glass surface, the page background and `Digits` for
  the big numbers (measured unbounded so the font's tall line box does not clip them).
- `Common.kt`: the tab bar (its pill slides to the open tab), glass buttons, the switch, and `rememberNow()`, the time updated once a
  minute and only while the app is visible.
- `ClockScreen.kt`, `WorldScreen.kt`, `AlarmsScreen.kt`, `TimerScreen.kt`, `StopwatchScreen.kt`:
  one file per tab.
- `Places.kt`: cities (the catalog in `assets/cities.tsv` and your chosen ones in preferences), UTC
  labels, offsets between zones, sunrise and sunset (the standard sunrise equation), and where the sun
  is overhead, for the map's night side.
- `CityMap.kt`: the full-screen city map (add, view and remove cities). Land and cities are two layers
  that a drag only moves. The land layer draws only the detailed tiles on screen (the coarse world when
  zoomed out) and is drawn again only when the zoom or the set of tiles changes: drawing the whole
  detailed coastline every frame made dragging janky (67% of frames on the S24 Ultra, against 2-3% now).
  It is drawn in the activity's window, not a dialog (a new window cost about 100 ms per open).

Smoothness at 60 Hz (S24 Ultra in Standard mode, release build, 2026-11-10): very big glyphs are drawn as
outlines rather than from Android's glyph cache, so `Digits`, the Clock page's dotted strip and the World
page's map card each draw into a kept layer (`CompositingStrategy.Offscreen`) and are not drawn again
while nothing in them changes. Tab switching went from 6-7% to 2-2.5% janky frames (legacy count),
the city map opens in about 60-75 ms instead of 165 ms.

- `Overlay.kt`: content drawn over the whole app in the activity's own window (`Overlay`, hosted by
  `ClockApp`), and `ElaraSheet`, the Elara bottom sheet built on it (dimmed backdrop, slides up, no drag,
  Back or a tap outside closes, rises above the keyboard). Material's `ModalBottomSheet` opens a new window
  each time, which made opening the alarm sheet take about 150 ms over three frames at 60 Hz; it now
  takes one frame of about 55 ms (building the form and its time wheels), then slides.
- `WorldMap.kt`: the dotted map from a coarse hand-made land mask (the Clock page's time-zone strip),
  drawn in one `drawPoints` call; `Land`, the solid land shapes (coarse and tiled) for the World page and
  the city map; and `nightShade`, the night side as a shape.
- `Bs.kt`, `BsTable.kt`: AD to Bikram Sambat dates, copied from Elara Calendar (no shared code at
  run time).

- `Alarm.kt`: an alarm, its JSON form, when it next rings, and its days label.
- `AlarmEngine.kt`: the alarms in device-protected storage (written before returning, so a receiver's
  write is never lost), scheduling with `setAlarmClock`, ringing through a full-screen notification on
  the alarm volume, snooze and dismiss; `AlarmReceiver` (ring, snooze, dismiss) and
  `RescheduleReceiver` (boot, locked boot, update, time and time-zone changes).
- `AlarmRingService.kt`: only for gradual volume. Android plays a notification's sound at one volume, so
  the app plays the alarm sound itself (alarm usage, partial wake lock while playing) and raises it over a
  minute, in a `systemExempted` foreground service (allowed for apps holding USE_EXACT_ALARM) that runs
  only while an alarm rings and shows the alarm's own notification. If Android will not start it, or no
  sound plays, the alarm rings through its notification as usual. Tested on the S24 Ultra 2026-11-10:
  started on time, stopped on Dismiss and after the ring time.
- `Settings.kt`: the settings, in device-protected storage so alarms read them before the first unlock.
- `SettingsPage.kt`: the Settings page, drawn over the app.
- `RingActivity.kt`: the ringing screen over the lock screen.
- `ClockApplication.kt`: sets alarms again whenever the process starts.
