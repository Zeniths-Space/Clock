# Elara Clock

A private, offline Android clock. Part of the Zenith's Space app collection.
Application ID `com.elara.clock`.

**Status:** alarms work: they are saved, ring on time over the lock screen, snooze and survive a restart,
and are added and edited in a sheet. The timer and stopwatch still count only while the app is open and
do not alert yet.

## Alarms

- Each alarm that is on is set with Android's alarm clock (`AlarmManager.setAlarmClock`): exact,
  allowed in Doze, shown as the alarm icon in the status bar. Nothing runs between alarms.
- When it goes off, a full-screen alarm shows over the lock screen with the time, Snooze (10 min) and
  Dismiss. The sound is the phone's default alarm sound on the alarm volume, repeating until answered
  (it rings with the ringer on silent and through Do not disturb's alarm exception), and stops after 10
  minutes if nobody answers. Back does not silence it.
- A one-time alarm turns itself off after ringing; a repeating one is set for its next day. Times are
  wall-clock times, so daylight-saving changes keep 07:00 at 07:00.
- Alarms are set again after a restart (also before the phone is first unlocked: they are kept in
  device-protected storage), after an update, after a time or time-zone change, and whenever the app's
  process starts (One UI does not always deliver the update broadcast).
- Adding or editing: tap + (or an alarm) for a sheet laid out like Samsung's alarm editor in the Elara form
  design: hour, minute and AM/PM wheels, the next ring date ("Tomorrow · Tue, 10 Nov"), day circles
  (Sunday first; none chosen rings once), a name, and Sound, Vibration and Snooze switches; Delete when
  editing; Cancel and Save at the bottom. Its header shows how long until it rings. It does not drag;
  closing it with changes asks first. Sound off rings silently (vibration and the screen); Snooze off
  leaves only Dismiss.
- With no alarms: a glass clock, Add alarm, and one-tap alarms for 6:00, 7:00 or a 20-minute nap.
- The Alarms screen warns, with a Fix button, if notifications are off (alarms could not ring), if
  exact alarms are not allowed, or if full-screen alarms are not allowed.
- Limitation: like every Android app, after a force stop from App info the app gets no broadcasts until
  it is opened again, so alarms are not restored after a restart until then.

## Screens

It follows the system theme. Light is lavender glass on a pale ground: frosted white cards, the
brand purple ramp for the main tiles and buttons, thin oversized digits and small spaced-out labels,
all in the phone's own font. Dark is AMOLED, like every Elara app: a black page, #141414 cards with a
#1F1F1F edge, white text and #B69CFF purple text. A small floating tab bar sits centred at the
bottom, icons only; a dark pill (purple in dark mode) marks the open tab and slides to the next.

- **Clock**: your city's time in huge thin digits, beside a line through your time zone on a dotted
  world map; a purple Today tile (date, ISO week, the Nepali BS date in Devanagari); a sun tile
  with sunrise, sunset and hours of daylight (computed on the phone); round chips for other cities.
  The title opens a menu of your city and the cities you added: the page then shows that city (so do
  the chips). The button at the top right opens Settings.
- **World clock**: a night-blue map with solid land, the night side shaded where the sun is down now and
  every city's time in a bubble (placed so none overlap), then each city with its UTC offset, whether
  it is yesterday, today or tomorrow there, how far ahead or behind it is, and its time. The + button
  opens a full-screen city map zoomed in on your city: drag, pinch or double-tap to zoom, tap any of
  about 450 cities (or search for one) and add it. Tapping a city in the list opens the map on it, with
  Back and Remove in the header.
- **Alarms**: the next alarm as stacked hours over minutes with a dotted line from now to when it
  rings, then the alarms as glass tiles with on/off switches. A 12h/24h switch (the same setting as in
  Settings).
- **Settings**: time format (phone, 12-hour, 24-hour), theme (phone, light, dark), snooze length (5 to
  20 minutes), how long an alarm rings before it stops (1 to 30 minutes), gradual volume (starts quietly,
  full after a minute), and the home city (automatic from the phone's time zone, an added city, or any
  city chosen on the map).
- **Timer**: hours, minutes and seconds in heavy stacked rows, a half-circle preset dial
  (1 to 45 minutes) that turns to the chosen preset, Reset, Start/Pause and +1 minute.
- **Stopwatch**: minutes, seconds and hundredths in the same rows, with laps.

The home city is the phone's time zone. The city list (`app/src/main/assets/cities.tsv`) is made
offline by `tools/cities.py` from the tz database's `zone.tab`, plus large cities that share a zone;
your chosen cities are kept in the app's preferences. The World page's map and the city map draw land as solid
shapes (`assets/land.bin`), made by `tools/land.py` from Natural Earth's public-domain 1:50m land
polygons: a coarse world, and a detailed version cut into 10-degree tiles for zooming in.
Nothing is looked up online.

## Build

Requires JDK 17 and the Android SDK (compileSdk 37).

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds install as `com.elara.clock.dev`, beside a release build. Release builds are signed
when the Gradle property `elaraClockSigning` points to a `keystore.properties` file
(storeFile, storePassword, keyAlias, keyPassword) outside this repository.

The app icon is generated: edit and run `python3 tools/icon.py`. It is a clock face filling the icon,
hands at ten past ten; it does not change with the time.

## Documentation

[ARCHITECTURE.md](ARCHITECTURE.md), [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md),
[ROADMAP.md](ROADMAP.md), [CONTRIBUTING.md](CONTRIBUTING.md).
