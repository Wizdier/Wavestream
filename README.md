# Wavestream

A modern media center built with Kotlin Multiplatform + Compose Multiplatform.
A fork-style reimplementation of CloudStream 3 with a Compose Multiplatform UI skin.

> **Goal**: Take CloudStream's proven plugin/repository/extractor infrastructure and put a fresh,
> modern Compose Multiplatform UI on top — for both Android and Desktop.

## What's working right now

### ✅ Stremio addon support (HTTP-based, native)
- Add any Stremio addon by manifest URL (`https://.../manifest.json`)
- Stremio addons are wrapped as `MainAPI` providers, so they integrate seamlessly with Home, Search, Details
- Catalogs fetched from `/catalog/{type}/{id}.json` with pagination
- Metadata fetched from `/meta/{type}/{id}.json`
- Streams fetched from `/stream/{type}/{videoId}.json`
- Subtitles and proxy headers from `behaviorHints` are passed through to the player
- API key in manifest URL query string is propagated to all resource URLs
- Tested with the official Cinemeta addon (`https://v3-cinemeta.strem.io/manifest.json`)

### ✅ CloudStream repository browsing & plugin installation
- Add any CloudStream repository URL (handles `https://`, `cloudstreamrepo://`, `https://cs.repo/` formats)
- Auto-converts `raw.githubusercontent.com` URLs to jsdelivr CDN for faster downloads
- Browse the plugin list from each repository
- Install plugins with one tap — download + load + register immediately (no restart needed)
- SHA-256 hash verification on download
- Plugin updates automatically when the repo has a newer version
- Plugin removal also unloads the provider and deletes the file
- Default repositories seeded on first launch (official CloudStream extensions repo)

### ✅ Plugin loading (platform-aware)
- **Android**: Loads `.cs3` files (ZIP with classes.dex + manifest.json) via `PathClassLoader`
  - Sets file read-only for Android 14+ compatibility
  - Reads manifest.json from inside the .cs3 file
  - Reflects out the plugin class and instantiates it
- **Desktop**: Loads `.jar` files via `URLClassLoader`
  - Reads manifest.json from inside the .jar if present
  - Falls back to scanning all classes for ones extending `BasePlugin` when manifest is missing
  - CloudStream's `.jar` files don't include manifest.json, so the scanner finds the plugin class automatically
- `SitePlugin.bestUrlForPlatform()` picks the right URL (.jar on Desktop, .cs3 on Android)

### ✅ Built-in extractors (11 hosts)
- StreamTape, MixDrop, Doodstream, Voe, Filemoon, JWPlayer, Upstream, Sendvid, Mp4Upload, Vidoza, M3U8 Manifest
- Each handles a specific video-hosting site's URL pattern

### ✅ UI (Compose Multiplatform, CloudStream-inspired)
- **Home**: Parallax hero, continue watching rail, bookmarks rail, per-provider rails with parallel fetching
- **Search**: Debounced query (300ms), provider filter chips, per-provider result counts, recent searches history
- **Details**: Backdrop hero with gradient overlay, plot/tags, episodes list, recommendations, "Sources" button for manual stream picker
- **Library**: Tabs for Bookmarks / Watching / Watched
- **Downloads**: Offline playback list
- **Settings**: Extensions, General, UI, Player, Updates, About
- **Extensions**: Repository management with expandable plugin lists, Stremio addon management, active providers list, installed plugin files list, recent activity log, init log viewer
- **Player**: Play/pause/seek, source picker, subtitle picker, auto-hide controls
- Dark-first Material 3 theme with indigo/violet accent
- Shimmer loading placeholders on poster cards
- Pressed scale animation + border highlight on cards
- Snackbar feedback for all actions

## Architecture

```
wavestream/
├── app/                                # Android application module
│   └── src/androidMain/kotlin/com/wavestream/app/
│       ├── MainActivity.kt              # Single-activity entry point
│       └── WaveStreamApp.kt             # Application class
├── shared/                             # KMP shared module (Android + Desktop)
│   └── src/
│       ├── commonMain/kotlin/com/wavestream/
│       │   ├── App.kt                   # Root composable + NavHost
│       │   ├── api/                     # MainAPI, ExtractorApi, APIHolder, Models
│       │   ├── plugins/
│       │   │   ├── BasePlugin.kt        # Plugin base class
│       │   │   ├── PluginManager.kt     # Download/load/unload lifecycle
│       │   │   ├── repository/          # RepositoryManager (CloudStream repos)
│       │   │   ├── stremio/             # StremioAddonClient + StremioProviderAdapter
│       │   │   ├── js/                  # JsPluginRuntime (Mozilla Rhino)
│       │   │   ├── m3u8/                # M3U8 helper
│       │   │   └── extractors/          # 11 built-in extractors
│       │   ├── core/
│       │   │   ├── WaveAppInit.kt       # Boot orchestrator (extractors → Stremio → plugins → repos)
│       │   │   ├── network/             # Ktor NetworkClient + WebViewResolver
│       │   │   ├── storage/             # PlatformStorage abstraction
│       │   │   ├── auth/                # AccountManager + BiometricAuthenticator
│       │   │   ├── cast/                # CastManager
│       │   │   ├── sync/                # SyncAPI (Trakt/MAL etc.)
│       │   │   ├── backup/              # BackupManager
│       │   │   └── updater/             # InAppUpdater
│       │   └── features/                # All UI screens
│       │       ├── home/                # HomeScreen
│       │       ├── search/              # SearchScreen + SearchHistoryRepository
│       │       ├── details/             # DetailsScreen
│       │       ├── player/              # PlayerScreen + gestures + subtitles + skip
│       │       ├── library/             # LibraryScreen
│       │       ├── downloads/           # DownloadsScreen + VideoDownloadManager
│       │       ├── extensions/          # ExtensionsScreen (the one you'll spend time in)
│       │       ├── settings/            # SettingsScreen
│       │       ├── bookmarks/           # BookmarkRepository
│       │       ├── watchprogress/       # WatchProgressRepository
│       │       ├── subscriptions/       # SubscriptionRepository
│       │       ├── notifications/       # EpisodeReleaseNotifications
│       │       ├── trailer/             # InAppYouTubeExtractor
│       │       ├── trakt/               # TraktApi
│       │       ├── debrid/              # DebridProviders
│       │       ├── tmdb/                # TmdbService
│       │       └── tv/                  # TV layout (Android TV / Desktop)
│       ├── androidMain/                 # Android-specific implementations
│       └── desktopMain/                 # Desktop (JVM) implementations
└── gradle/
    └── libs.versions.toml               # Version catalog
```

## Tech Stack

- **Kotlin**: 2.1.20
- **Gradle**: 9.4.1 (with foojay toolchain resolver for auto JDK download)
- **AGP**: 8.8.1
- **Compose Multiplatform**: 1.7.3
- **Ktor**: 3.0.3 (OkHttp on Android, Java on Desktop)
- **Coil**: 3.0.4 (image loading with shimmer placeholders)
- **Media3 / ExoPlayer**: 1.5.1 (Android only — Desktop uses JavaFX media)
- **Mozilla Rhino**: 1.7.15 (JS plugin runtime — pure JVM, works on both platforms)
- **Kotlinx Serialization**: 1.7.3
- **Kotlinx Coroutines**: 1.9.0
- **Navigation Compose**: 2.9.0

## Build Instructions

### Desktop (JVM)
```bash
./gradlew :shared:desktopJar
# Output: shared/build/libs/shared-desktop.jar
```

### Android (requires Android SDK)
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
6. **Watch the activity log**: The Extensions screen shows recent plugin load/unload/fail events and init logs at the bottom.

## Known Limitations

- **CloudStream .cs3 plugins** are Android-only (contain DEX bytecode). Desktop requires the `.jar` variant, which CloudStream's official repo provides.
- **JS plugin runtime** is implemented but not yet exposed in the UI. The `JsPluginRuntime` class is ready to use programmatically.
- **Torrent streams** from Stremio addons (infoHash) are not yet supported — requires a torrent engine.
- **Desktop player** uses a basic JavaFX media player. For full codec support on Desktop, consider installing a media codec pack.

## License

MIT — see [LICENSE](LICENSE).

## Credits

- [CloudStream](https://github.com/recloudstream/cloudstream) — Original plugin/repository/extractor infrastructure design
- [Stremio](https://www.stremio.com/) — Addon protocol specification
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) — UI framework
