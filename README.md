# Wavestream

A Compose Multiplatform fork of [CloudStream 3](https://github.com/recloudstream/cloudstream) — a media streaming app that loads community-built plugins (`.cs3` for Android, `.jar` for desktop) to aggregate free movie, series, anime, and live TV sources.

> **Status:** Working skeleton. Library module compiles against the real CloudStream plugin API; Compose Multiplatform UI runs on Android and Desktop JVM.

## Architecture

Wavestream has three modules:

| Module | Purpose |
| --- | --- |
| `library/` | Verbatim copy of CloudStream 3's `library` module, using `com.lagradost.cloudstream3.*` packages so real CloudStream plugins load without modification. |
| `shared/` | Compose Multiplatform UI module (`com.wavestream.*`). All screens (Home, Search, Details, Player, Library, Downloads, Extensions, Settings), Stremio addon support, theme, and platform abstractions. |
| `app/` | Android application entry point. |

**Key insight**: The library module uses the *exact same package names* as CloudStream (`com.lagradost.cloudstream3.MainAPI`, `com.lagradost.cloudstream3.plugins.BasePlugin`, etc.). This means real CloudStream `.cs3` (Android) and `.jar` (Desktop) plugins load successfully because they were compiled against those same package names.

## Features

- **Plugin loading** via `PluginManager` — drop `.cs3` or `.jar` files into the Extensions directory and they're auto-discovered at boot.
- **Repository management** — add CloudStream repo URLs (`cloudstreamrepo://...`, `https://cs.repo/...`, or raw GitHub URLs) to browse and install plugins.
- **Stremio addon support** — wrap a Stremio addon URL as a native `MainAPI` provider so its catalog and streams appear alongside CS providers.
- **Compose Multiplatform UI** — same Kotlin code renders on Android (phones/tablets) and Desktop JVM (Linux/macOS/Windows).
- **Material 3 theming** with a dark ocean-blue palette tuned for long-form video consumption.
- **Shimmer loading placeholders**, press-scale poster cards, debounced multi-provider search.

## Build

### Prerequisites

- JDK 17 (the project uses the Gradle toolchain to auto-download if missing)
- Android SDK with `platform-35` and `build-tools;35.0.0` (for the Android target)
- Internet access (Gradle pulls dependencies from Maven Central, Google, JitPack, and JetBrains' Compose dev repo)

> **minSdk note:** All three modules use `minSdk = 26` (Android 8.0). This is required because Rhino 1.9.1 (the JS engine used by CloudStream's library to evaluate extractor scripts) uses `MethodHandle.invoke` / `invokeExact`, which only became available in Android API 26.

### Verify the build (Desktop only)

```bash
chmod +x ./gradlew        # only needed once after extracting the zip
./gradlew :library:compileKotlinDesktop :shared:compileKotlinDesktop
```

This is the canonical verification target — both modules compile against the JVM target.

> **Tip:** If you see `./gradlew: Permission denied`, run `chmod +x ./gradlew`. The GitHub Actions workflow already does this automatically.

### Run the desktop app

```bash
./gradlew :shared:run
```

### Build a desktop distribution

```bash
./gradlew :shared:packageDistributionForCurrentOS
# Outputs land in shared/build/compose/binaries/{main,msi,dmg,deb}/
```

### Build the Android APK

```bash
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
wavestream/
├── library/                       # CloudStream library module (patched)
│   └── src/
│       ├── commonMain/kotlin/com/lagradost/cloudstream3/
│       │   ├── MainAPI.kt         # Patched: dayOfMonth() fix, stremio:// pass-through
│       │   ├── utils/AtomicList.kt# Rewritten without kotlinx.atomicfu
│       │   ├── utils/ExtractorApi.kt  # Patched: YoutubeExtractor removed
│       │   └── plugins/
│       │       ├── PluginManager.kt       # Object that loads .cs3/.jar plugins
│       │       ├── BasePlugin.kt          # Base class for all plugins
│       │       ├── RepositoryManager.kt   # CS repository parsing + plugin download
│       │       └── Platform.kt            # expect/actual isDesktopPlatform()
│       ├── androidMain/           # Android-specific: PathClassLoader-based plugin loading
│       └── desktopMain/           # Desktop-specific: URLClassLoader-based plugin loading
├── shared/                        # Compose Multiplatform UI
│   └── src/
│       ├── commonMain/kotlin/com/wavestream/
│       │   ├── App.kt             # Root composable + NavHost + side-channel nav args
│       │   ├── WaveAppInit.kt     # Boot orchestrator (loads plugins, exposes StateFlow)
│       │   ├── RepositoryStore.kt # Bridges platform prefs <-> RepositoryManager
│       │   ├── platform/          # expect/actual platform abstraction
│       │   ├── stremio/           # Stremio addon client + provider adapter
│       │   └── ui/
│       │       ├── theme/         # WaveTheme, WaveTypography
│       │       ├── components/    # PosterCard (shimmer+press), States, WaveBottomBar
│       │       ├── player/        # WaveVideoPlayer expect/actual
│       │       ├── library/       # LibraryStore + LibraryEntry
│       │       └── screens/       # home, search, details, player, library, downloads, extensions, settings
│       ├── androidMain/           # Android Context init, AndroidPreferences
│       └── desktopMain/           # Desktop entry point + JSON-file preferences
├── app/                           # Android application
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/wavestream/app/MainActivity.kt
├── gradle/
│   ├── libs.versions.toml         # All version pins and library coordinates
│   └── wrapper/                   # Gradle 8.10.2
├── .github/workflows/build.yml    # CI: Desktop JVM + Android APK
├── settings.gradle.kts
├── build.gradle.kts
└── gradle.properties
```

## Verification

After building, verify real CloudStream plugins load:

1. Download `https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.jar`
2. Drop the file into `~/.wavestream/Extensions/` (desktop) or `/sdcard/Android/data/com.wavestream.app/files/Wavestream/Extensions/` (Android)
3. Launch the app — the Extensions tab should list "DailymotionProvider"
4. Search "test" — Dailymotion results should appear

The build succeeds when `./gradlew :shared:compileKotlinDesktop` passes.

## Patches Applied to the CloudStream Library

The library module is a verbatim copy of CloudStream 3's `library/` module with these patches:

1. **Removed files requiring unavailable dependencies**:
   - `metaproviders/` (TMDB/Trakt/MyDramaList sync providers)
   - `extractors/YoutubeExtractor.kt` (uses Android MediaController)

2. **`AtomicList.kt` rewritten** — removes `kotlinx.atomicfu` dependency; uses plain `synchronized(this) { ... }` instead. Public API is identical.

3. **`MainAPI.kt` patched**:
   - `isUpcoming()`: `day()` → `dayOfMonth()` (the older API was removed in kotlinx-datetime 0.6.0)
   - `fixUrl()`: added `stremio:` to the pass-through list so synthetic Stremio deep-links aren't mangled

4. **`ExtractorApi.kt` patched** — removed `YoutubeExtractor` imports and entries from `extractorApis`.

5. **New plugin infrastructure** in `library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/`:
   - `PluginManager.kt` — object that scans a directory and loads each `.cs3`/`.jar`
   - `BasePlugin.kt` — abstract base class with `registerMainAPI`/`registerExtractorAPI`/`CloudstreamPlugin` annotation
   - `RepositoryManager.kt` — parses `repo.json`, downloads plugins with SHA-256 verification and atomic moves
   - Platform-specific `PluginManager.android.kt` (PathClassLoader) and `PluginManager.desktop.kt` (URLClassLoader with class scanning fallback)
   - `Platform.kt` expect/actual for `isDesktopPlatform()`

## License

GPL-3.0 — inherited from CloudStream 3. See [LICENSE](LICENSE).

## Credits

- [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream) — the upstream project this is forked from. All credit for the plugin architecture, provider API, and extractor ecosystem goes to the CloudStream team.
- [JetBrains Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) — the UI framework that lets us share the entire UI layer across Android and Desktop.
- [coil-kt/coil3](https://github.com/coil-kt/coil) — image loading with shimmer-friendly state callbacks.
