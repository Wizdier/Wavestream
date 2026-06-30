# Wavestream — Distribution Package

This package contains the complete Wavestream project source plus prebuilt desktop and Android binaries.

## Contents

```
wavestream/
├── README.md                  # Project overview, architecture, build instructions
├── GITHUB_PUSH.md             # How to push this repo to your GitHub
├── STREMIO.md                 # How Stremio addon support works
├── LICENSE                    # GPL-3.0 (inherited from CloudStream 3)
├── local.properties.template  # Copy to local.properties and set Android SDK path
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat      # Gradle 8.10.2 wrapper
├── gradle/
│   ├── libs.versions.toml     # All version pins
│   └── wrapper/
├── .github/workflows/
│   ├── build.yml              # CI: desktop JVM + Android APK on every push
│   └── release.yml            # Release: builds APK + jar on tag push, uploads to Release
│
├── library/                   # Patched CloudStream 3 library module
├── shared/                    # Compose Multiplatform UI module
│   └── src/commonMain/kotlin/com/wavestream/
│       ├── DefaultRepos.kt             # Pre-seeded CS repos + Stremio addons
│       ├── WaveAppInit.kt              # Boot orchestrator (seeds defaults, loads plugins)
│       ├── App.kt                      # NavHost (honors defaultTab setting)
│       ├── stremio/                    # Stremio addon -> MainAPI adapter
│       ├── platform/                   # expect/actual platform abstraction
│       └── ui/
│           ├── settings/               # Anikku-inspired Preference DSL
│           │   ├── Preference.kt
│           │   ├── WavePreferences.kt
│           │   └── widget/
│           │       ├── PreferenceItemWidget.kt
│           │       └── PreferenceScaffold.kt
│           ├── screens/
│           │   ├── home/               # Hero banner + rails (CloudStream-style)
│           │   ├── player/             # ExoPlayer-backed on Android, external on Desktop
│           │   ├── settings/           # 8 category sub-screens
│           │   └── ...                 # search, details, library, downloads, extensions
│           └── player/                 # WaveVideoPlayer expect/actual
├── app/                       # Android application entry point
│
└── dist/
    ├── Wavestream-linux-x64-1.0.0.jar  # Prebuilt desktop uber jar (Linux x64, 88 MB)
    └── Wavestream-1.0.0-debug.apk      # Prebuilt Android debug APK (30 MB)
```

## Quickstart — Run the Desktop App

You need Java 17 or later installed on your system.

```bash
java -jar dist/Wavestream-linux-x64-1.0.0.jar
```

On first launch, the app:
1. Creates `~/.wavestream/` with subdirectories (`Extensions/`, `Downloads/`, `preferences.json`)
2. Seeds 5 default CloudStream repositories and 2 Stremio addons (Cinemeta + OpenSubtitles)
3. Loads any `.cs3`/`.jar` plugins from the `Extensions/` directory
4. Renders the home screen with a hero banner + category rails

## Quickstart — Install the Android APK

```bash
adb install dist/Wavestream-1.0.0-debug.apk
```

Or copy the APK to your Android device and tap it in a file manager (enable "Install from unknown sources" first).

The APK targets:
- `minSdk = 26` (Android 8.0) — required because Rhino 1.9.1 uses `MethodHandle.invoke`
- `targetSdk = 35` (Android 15)
- `applicationId = com.wavestream.app`

## What's New in This Version

Based on user feedback, this version adds:

### 1. Default repos + Stremio addons pre-seeded
No more blank Extensions screen. On first launch, the app auto-installs:
- **CS repos:** milkman, nice, jeremy, stremio, automations (cs.repo short URLs)
- **Stremio addons:** Cinemeta (TMDB/IMDb metadata), OpenSubtitles

The seeding is gated by a one-shot preferences flag, so user customizations are preserved across re-seeds. The Settings → Advanced → "Restore default repos and addons" button forces a re-seed.

### 2. Anikku-inspired Settings screen
Built a Preference DSL (`Preference.kt` + `widget/PreferenceItemWidget.kt` + `widget/PreferenceScaffold.kt`) modeled after [komikku-app/anikku](https://github.com/komikku-app/anikku). The root Settings screen lists 8 categories; tapping one pushes into a sub-screen:

- **General** — default tab, auto-rescan, jsDelivr proxy, boot status
- **Appearance** — theme mode, poster card width, quality badges
- **Streaming** — default quality, prefer HLS, prefer Stremio streams
- **Player** — playback speed, remember position, gestures, PiP
- **Subtitles** — enable, language (12 options), font size
- **Network** — request timeout, concurrent requests
- **Advanced** — verbose logging, restore defaults, rescan, safe mode
- **About** — version, plugins loaded, providers, data dirs, credits

All settings persist via `WavePreferences` (typed accessor over `wavePlatform.preferences`).

### 3. CloudStream-style Home screen with hero banner
- 280dp hero banner at the top with gradient overlay, "FEATURED" tag, and "View details" button
- The first item of the first provider section becomes the hero
- Each section shows an item count next to its title
- Empty state offers a "Rescan now" CTA instead of sitting blank

### 4. Player improvements
- **Android:** renders an ExoPlayer `PlayerView` with controls, fast-forward/rewind, auto-hide
- **Desktop:** renders a placeholder; the URL opens in the system media player
- Proper `DisposableEffect` releases the player when leaving the screen
- Error state with Retry + Go back + Open in external player fallback
- Back button has a circular semi-transparent background so it's visible over video

### 5. Release workflow
`.github/workflows/release.yml` triggers on any `v*` tag push:
- Builds `:app:assembleDebug` → `wavestream-{tag}-android.apk`
- Builds `:shared:packageUberJarForCurrentOS` → `wavestream-{tag}-linux-x64.jar`
- Creates a GitHub Release with auto-generated notes
- Uploads both artifacts
- Marks as pre-release if the tag contains a hyphen (e.g. `v1.0.0-rc1`)

To cut a release:
```bash
git tag v0.1.0
git push origin v0.1.0
```

Also supports `workflow_dispatch` for manual testing with a custom tag.

## Quickstart — Build From Source

### Desktop verification (canonical)

```bash
chmod +x ./gradlew        # only needed once after extracting the zip
./gradlew :library:compileKotlinDesktop :shared:compileKotlinDesktop
```

### Run desktop UI from source

```bash
./gradlew :shared:run
```

### Build Android APK

```bash
cp local.properties.template local.properties
# Edit local.properties to point to your Android SDK
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Pushing to Your GitHub

See [GITHUB_PUSH.md](GITHUB_PUSH.md) for three push options (HTTPS+PAT, gh CLI, SSH). The repo is already git-initialized with 6 commits and `origin` pointing to `https://github.com/wizdier/wavestream.git`.

## Verification Status

All targets build successfully:

- ✅ `./gradlew :library:compileKotlinDesktop` — passes
- ✅ `./gradlew :shared:compileKotlinDesktop` — passes
- ✅ `./gradlew :shared:desktopJar` — passes
- ✅ `./gradlew :shared:packageUberJarForCurrentOS` — passes (this jar is in `dist/`)
- ✅ `./gradlew :app:assembleDebug` — passes (this APK is in `dist/`)

## Build Environment Used

- Java: OpenJDK 21.0.11
- Gradle: 8.10.2 (via wrapper)
- Kotlin: 2.2.20
- Compose Multiplatform: 1.8.0
- Android SDK: `platform-35` + `build-tools;35.0.0`
- OS: Debian 13 (Linux x64)
