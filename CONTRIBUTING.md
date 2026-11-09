# Contributing

1. Read [ARCHITECTURE.md](ARCHITECTURE.md) and [PRIVACY.md](PRIVACY.md).
2. Keep changes small and focused, with one topic per pull request.
3. Add or update unit tests for any behavior change in `Places.kt`, `AlarmsScreen.kt` (`nextRing`, `daysLabel`) or `Bs.kt`.
4. Run `./gradlew assembleDebug testDebugUnitTest lintDebug` before opening a PR.
5. Do not add network access, telemetry or polling. Alarm permissions and background work come only with the alarm engine, and only what it needs.
6. Explain any new dependency in the PR: why it is needed, its license, and the alternatives.
