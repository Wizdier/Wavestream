# Wavestream

A CloudStream 3 fork with Compose Multiplatform UI for Android + Desktop.

## Architecture
- `library/` — Verbatim CloudStream 3 library (com.lagradost.cloudstream3.*)
- `shared/` — Compose Multiplatform UI (com.wavestream.*)
- `app/` — Android application

Real CloudStream plugins (.cs3/.jar) load without modification.

## Build
Desktop: `./gradlew :shared:desktopJar`
Android: `./gradlew :app:assembleDebug` (set sdk.dir in local.properties)

## License
MIT
