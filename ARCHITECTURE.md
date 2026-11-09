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
- `Places.kt`: cities, UTC labels, offsets between zones, sunrise and sunset (the standard sunrise
  equation).
- `WorldMap.kt`: the dotted map from a coarse hand-made land mask, drawn in one `drawPoints` call.
- `Bs.kt`, `BsTable.kt`: AD to Bikram Sambat dates, copied from Elara Calendar (no shared code at
  run time).

Not yet built: storage, the alarm engine (exact alarms, the ringing screen, notifications, boot and
time-zone change handling).
