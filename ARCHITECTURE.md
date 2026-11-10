# Architecture

One app module, Jetpack Compose, no other libraries.

- `MainActivity.kt`: the activity, splash hand-off and `ClockApp`, which swaps the five tabs and
  floats the tab bar over them.
- `Theme.kt`: the colours (`Ink`, light and AMOLED dark), text styles in the system font, the glass surface, the page background and `Digits` for
  the big numbers (measured unbounded so the font's tall line box does not clip them).
- `Common.kt`: the tab bar (its pill slides to the open tab), glass buttons, the switch, and `rememberNow()`, the time updated once a
  minute and only while the app is visible.
- `ClockScreen.kt`, `WorldScreen.kt`, `AlarmsScreen.kt`, `TimerScreen.kt`, `StopwatchScreen.kt`:
  one file per tab.
- `Places.kt`: cities (the catalog in `assets/cities.tsv` and your chosen ones in preferences), UTC
  labels, offsets between zones, sunrise and sunset (the standard sunrise equation), and where the sun
  is overhead, for the map's night side.
- `CityMap.kt`: the full-screen city map (add, view and remove cities). The land and city dots are one
  layer that a drag only moves; it is drawn again only when the zoom or the chosen city changes, and the
  16 000 land dots go in one `drawRawPoints` call (a `List<Offset>` `drawPoints` records them one by one).
- `WorldMap.kt`: the dotted map from a coarse hand-made land mask, drawn in one `drawPoints` call, and
  `Land`, the detailed 1-degree land for the city map.
- `Bs.kt`, `BsTable.kt`: AD to Bikram Sambat dates, copied from Elara Calendar (no shared code at
  run time).

- `Alarm.kt`: an alarm, its JSON form, when it next rings, and its days label.
- `AlarmEngine.kt`: the alarms in device-protected storage (written before returning, so a receiver's
  write is never lost), scheduling with `setAlarmClock`, ringing through a full-screen notification on
  the alarm volume, snooze and dismiss; `AlarmReceiver` (ring, snooze, dismiss) and
  `RescheduleReceiver` (boot, locked boot, update, time and time-zone changes).
- `RingActivity.kt`: the ringing screen over the lock screen.
- `ClockApplication.kt`: sets alarms again whenever the process starts.
