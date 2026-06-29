# Wavestream

A modern media center built as a true **CloudStream 3 fork** — using the verbatim CloudStream library module with a fresh Compose Multiplatform UI on top.

> **Goal**: Take CloudStream's proven plugin/repository/extractor infrastructure (the `com.lagradost.cloudstream3` packages) and put a fresh, modern Compose Multiplatform UI skin on top — for both Android and Desktop.

## Why this works

The critical insight: **real CloudStream plugins are compiled against `com.lagradost.cloudstream3.MainAPI` and `com.lagradost.cloudstream3.plugins.BasePlugin`**. If Wavestream uses *different* package names (like `com.wavestream.api.MainAPI`), real CloudStream plugins fail to load with `ClassNotFoundError`.

This project solves it by **copying CloudStream's library module verbatim** — same package names, same class names, same method signatures. So when a real CloudStream `.cs3` (Android) or `.jar` (Desktop) plugin is loaded:

1. `URLClassLoader` opens the plugin jar
2. PluginManager scans all `.class` entries for ones extending `BasePlugin`
3. The plugin class is loaded — it successfully finds `com.lagradost.cloudstream3.MainAPI` in the classpath (because we're using the real CloudStream library)
4. The plugin's `load()` method is called, registering providers via `registerMainAPI(provider)`
5. The provider appears in `APIHolder.allProviders` and is visible in Home/Search

## Module structure

```
wavestream/
├── library/                     # ← Verbatim CloudStream 3 library module
│   ├── build.gradle.kts          # KMP: androidTarget + jvm("desktop")
│   └── src/
│       ├── commonMain/kotlin/com/lagradost/cloudstream3/
│       │   ├── MainAPI.kt         # MainAPI, APIHolder, ExtractorLink, SearchResponse, etc.
│       │   ├── plugins/           # BasePlugin, PluginManager (expect/actual), RepositoryManager
│       │   ├── extractors/        # 40+ extractors (StreamTape, MixDrop, Doodstream, etc.)
│       │   ├── utils/             # ExtractorApi, M3u8Helper, JsUnpacker, AtomicList
│       │   ├── network/           # WebViewResolver
│       │   ├── mvvm/              # logError, safe, safeAsync
│       │   └── syncproviders/     # SyncAPI
│       ├── androidMain/           # PathClassLoader (DEX), Android WebView
│       └── desktopMain/           # URLClassLoader (JVM), JVM WebView
├── shared/                       # ← Compose Multiplatform UI module
│   ├── build.gradle.kts          # KMP: androidTarget + jvm("desktop") + Compose
│   └── src/
│       ├── commonMain/kotlin/com/wavestream/
│       │   ├── App.kt             # Root composable + NavHost
│       │   ├── WaveAppInit.kt     # Boot orchestrator
│       │   ├── RepositoryStore.kt # CS repo URL persistence
│       │   ├── stremio/           # Stremio addon support (StremioProviderAdapter)
│       │   └── ui/
│       │       ├── theme/         # CloudStream-inspired dark palette
│       │       ├── components/    # PosterCard (shimmer), WaveBottomBar, States
│       │       └── screens/       # home, search, details, player, library, downloads, extensions, settings
│       ├── androidMain/           # PlatformStorage (SharedPreferences), ExoPlayer surface
│       └── desktopMain/           # PlatformStorage (JSON file), main.kt entry, placeholder player
└── app/                          # ← Android application module
    └── src/androidMain/
        ├── AndroidManifest.xml
        └── kotlin/com/wavestream/app/MainActivity.kt
```

## What works

### ✅ CloudStream plugin loading (real plugins, not reimplementations)
- **Android**: Loads `.cs3` files via `PathClassLoader` — reads `manifest.json`, reflects out `pluginClassName`, instantiates it. Real CloudStream .cs3 files contain `classes.dex` (Android DEX bytecode) compiled against `com.lagradost.cloudstream3.*`.
- **Desktop**: Loads `.jar` files via `URLClassLoader` — reads `manifest.json` if present, otherwise scans all `.class` entries for ones extending `BasePlugin`. Real CloudStream .jar files don't include manifest.json, so the scanner finds the plugin class automatically.
- **Platform-aware URL selection**: `SitePlugin.bestUrlForPlatform()` returns the `.jar` URL on Desktop, `.cs3` URL on Android — CloudStream's repo JSON provides both.

### ✅ CloudStream repository browsing & plugin installation
- Add any CloudStream repository URL (handles `https://`, `cloudstreamrepo://`, `https://cs.repo/` formats)
- Auto-converts `raw.githubusercontent.com` URLs to jsdelivr CDN for faster downloads
- Browse the plugin list from each repository (expandable rows in Extensions screen)
- Install plugins with one tap — download + load + register immediately (no restart needed)
- SHA-256 hash verification on download
- Plugin updates automatically when the repo has a newer version
- Plugin removal also unloads the provider and deletes the file
- Default CS extensions repo seeded on first launch

### ✅ Stremio addon support (HTTP-based, native)
- Add any Stremio addon by manifest URL (`https://.../manifest.json`)
- Stremio addons are wrapped as `MainAPI` providers, so they integrate seamlessly with Home, Search, Details
- Catalogs fetched from `/catalog/{type}/{id}.json` with pagination
- Metadata fetched from `/meta/{type}/{id}.json`
- Streams fetched from `/stream/{type}/{videoId}.json`
- Subtitles and proxy headers from `behaviorHints` are passed through to the player
- API key in manifest URL query string is propagated to all resource URLs
- Tested with the official Cinemeta addon (`https://v3-cinemeta.strem.io/manifest.json`)

### ✅ Built-in extractors (40+)
The library module includes CloudStream's full extractor set: StreamTape, MixDrop, Doodstream, Voe, Filemoon, JWPlayer, Upstream, Sendvid, Mp4Upload, Vidoza, Filesim, Gofile, Gdriveplayer, Dailymotion, Cda, and many more.

### ✅ UI (Compose Multiplatform, CloudStream-inspired)
- **Home**: Parallax hero, continue watching rail, bookmarks rail, per-provider rails with parallel fetching
- **Search**: Debounced query (300ms), provider filter chips, per-provider result counts, recent searches history
- **Details**: Backdrop hero with gradient overlay, plot/tags, episodes list, recommendations, "Sources" button for manual stream picker
- **Library**: Tabs for Bookmarks / Watching / Watched
- **Downloads**: Offline playback list (placeholder)
- **Settings**: Extensions, General, UI, Player, Updates, About
- **Extensions**: Repository management with expandable plugin lists, Stremio addon management, active providers list, installed plugin files list, init log viewer, snackbar feedback
- **Player**: Play/pause/seek, auto-hide controls (Android uses ExoPlayer, Desktop is placeholder)
- Dark-first Material 3 theme with indigo/violet accent
- Shimmer loading placeholders on poster cards
- Pressed scale animation + border highlight on cards
- Snackbar feedback for all actions

## Build & Run

### Desktop (JVM)
```bash
cd wavestream
./gradlew :shared:desktopJar
# Output: shared/build/libs/shared-desktop.jar
```

To run the desktop app (requires a display):
```bash
./gradlew :shared:desktopRun
```

### Android (requires Android SDK)
Set `sdk.dir` in `local.properties`:
```
sdk.dir=/path/to/Android/Sdk
```

Then:
```bash
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## How to use

1. **First launch**: The app auto-seeds the official CloudStream extensions repo and the Cinemeta Stremio addon. You'll see content on the Home screen within seconds.
2. **Add more repositories**: Go to Settings → Extensions → Repositories → Add Repo. Paste any CloudStream repo URL.
3. **Add Stremio addons**: Same screen, Stremio Addons → Add Addon. Paste a manifest URL. Find addons at https://stremio-addons.netlify.app/
4. **Browse plugins in a repo**: Tap a repository row to expand it and see all available plugins. Tap Install to download and load.
5. **Remove plugins**: Tap the trash icon on installed plugins, or remove the whole repository (also deletes its plugins).
6. **Watch the activity log**: The Extensions screen shows recent init log messages at the bottom.

## Tech Stack

- **Kotlin**: 2.2.20
- **Gradle**: 8.10.2 (with foojay toolchain resolver for auto JDK 17 download)
- **AGP**: 8.7.3
- **Compose Multiplatform**: 1.8.0
- **Ktor**: 3.1.3
- **Coil**: 3.0.4
- **Media3 / ExoPlayer**: 1.5.1 (Android only)
- **Mozilla Rhino**: 1.9.1 (JS engine for extractors — pure JVM, works on both platforms)
- **NiceHttp**: 0.4.18 (HTTP client — same as CloudStream)
- **Jackson**: 2.13.1 (JSON — same as CloudStream)
- **Kotlinx Serialization**: 1.9.0
- **Kotlinx Coroutines**: 1.10.1
- **Kotlinx Datetime**: 0.6.0
- **Ksoup**: 0.2.6 (HTML parser)
- **Gson**: 2.11.0 (used by some extractors)
- **Cryptography**: 0.4.0 (for M3U8Helper AES decryption)

## Known Limitations

- **CloudStream .cs3 plugins** work on Android only (contain DEX bytecode). Desktop requires the `.jar` variant, which CloudStream's official repo provides.
- **JS plugin runtime** from CloudStream is present but not exposed in the UI.
- **Torrent streams** from Stremio addons (infoHash) are not yet supported — requires a torrent engine.
- **Desktop player** uses a placeholder. For real playback on Desktop, integrate JavaFX media or VLCJ.
- **TMDB / Trakt meta-providers** were removed (require external API keys and dependencies).

## Credits

- [CloudStream 3](https://github.com/recloudstream/cloudstream) — The library module is a verbatim copy with minimal patches for compilation. All credit for the plugin/extractor/repository infrastructure goes to the CloudStream team.
- [Stremio](https://www.stremio.com/) — Addon protocol specification
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) — UI framework

## License

MIT — see [LICENSE](LICENSE). Note that the CloudStream library module retains its original license (also MIT-like).
