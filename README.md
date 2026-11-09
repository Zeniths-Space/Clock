# Elara Clock

A private, offline Android clock. Part of the Zenith's Space app collection.
Application ID `com.elara.clock`.

**Status: interface only.** This first version is the approved design with working screens, but no
alarm engine yet: alarms are sample alarms kept in memory, and nothing rings, notifies or runs in
the background. The timer and stopwatch count only while the app is open.

## Screens

It follows the system theme. Light is lavender glass on a pale ground: frosted white cards, the
brand purple ramp for the main tiles and buttons, thin oversized digits and small spaced-out labels,
all in the phone's own font. Dark is AMOLED, like every Elara app: a black page, #141414 cards with a
#1F1F1F edge, white text and #B69CFF purple text. A small floating tab bar sits centred at the
bottom, icons only; a dark pill (purple in dark mode) marks the open tab and slides to the next.

- **Clock**: your city's time in huge thin digits, beside a line through your time zone on a dotted
  world map; a purple Today tile (date, ISO week, the Nepali BS date in Devanagari); a sun tile
  with sunrise, sunset and hours of daylight (computed on the phone); round chips for other cities.
- **World clock**: a night-blue dotted map with every city's time in a bubble, then each city with
  its UTC offset, how far ahead or behind it is, and its time.
- **Alarms**: the next alarm as stacked hours over minutes with a dotted line from now to when it
  rings, then the alarms as glass tiles with on/off switches. A 12h/24h switch.
- **Timer**: hours, minutes and seconds in heavy stacked rows, a half-circle preset dial
  (1 to 45 minutes) that turns to the chosen preset, Reset, Start/Pause and +1 minute.
- **Stopwatch**: minutes, seconds and hundredths in the same rows, with laps.

The home city is the phone's time zone. Cities known so far: Kathmandu, Delhi, Dubai, London,
New York and Tokyo.

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

The app icon is generated: edit and run `python3 tools/icon.py`. It is a clock face filling the icon
(hands at ten past ten). The script also writes the face and each hand as their own drawables, which
the launcher activity names in its meta-data, so Elara Launcher can show the live time on the icon.

## Documentation

[ARCHITECTURE.md](ARCHITECTURE.md), [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md),
[ROADMAP.md](ROADMAP.md), [CONTRIBUTING.md](CONTRIBUTING.md).
