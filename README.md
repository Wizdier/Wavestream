# Wavestream

A modern media center for Android built with Kotlin Multiplatform and Compose Multiplatform. Combines the best of CloudStream's extension system with NuvioMobile's Stremio addon + JS plugin support.

## Features

### Extension System (3 ways to add content)

1. **CloudStream-style providers** (`.ws3` DEX plugins)
   - Compile-time JAR containing `MainAPI` + `ExtractorApi` contracts
   - Runtime-loaded via `PathClassLoader` (Android) or `URLClassLoader` (Desktop)
   - Plugin manifest.json declares entry point class
   - Repository manager fetches plugin lists from JSON repo URLs
   - SHA-256 hash verification on download
   - Safe mode for recovery from boot-looping extensions
   - Full backwards compatibility with existing CloudStream extensions

2. **Stremio addons** (HTTP-based)
   - Fetch manifest from `/manifest.json`
   - Call `/catalog/{type}/{id}.json` for home pages
   - Call `/meta/{type}/{id}.json` for metadata
   - Call `/stream/{type}/{videoId}.json` for playable streams
   - URL-encoded path segments per RFC 3986
   - Query string propagation (for API key auth)

3. **JS plugin scrapers** (JavaScript)
   - Executed via Mozilla Rhino (works on Android + Desktop)
   - Polyfill injection: `fetch`, `URL`, `URLSearchParams`, `atob`/`btoa`, `console`, `AbortController`, `TextEncoder`/`TextDecoder`, `Promise`, `require`
   - Native bridges: `__native_fetch`, `__capture_result`, `__parse_url`
   - 60-second timeout per plugin execution
   - Returns JSON array of stream objects

### UI (CloudStream-inspired)

- **Home screen** with parallax hero + horizontal rails per provider
- **Search** with debounced query + parallel provider search + history
- **Details screen** with backdrop hero, cast row, episode list, recommendations
- **Library** with tabs for bookmarks/watching/watched
- **Downloads** for offline playback
- **Settings** with 9 categories (general, UI, player, providers, extensions, updates, account, backup, about)
- **Extensions** management screen for plugins + Stremio addons
- **Player** with play/pause/seek, prev/next episode, source picker, subtitle picker
- Dark-first Material 3 theme with indigo/violet accent

### Architecture

```
wavestream/
├── app/                        # Android application module
│   └── src/androidMain/
│       ├── AndroidManifest.xml
│       └── kotlin/com/wavestream/app/
│           ├── MainActivity.kt       # Splash + edge-to-edge + setContent
│           └── WaveStreamApp.kt      # Application class
├── shared/                     # KMP shared module
│   └── src/
│       ├── commonMain/         # All shared code
│       │   └── kotlin/com/wavestream/
│       │       ├── App.kt            # Root composable + NavHost
│       │       ├── api/              # MainAPI + ExtractorApi contracts
│       │       │   ├── MainAPI.kt    # The provider contract (CloudStream-style)
│       │       │   ├── Models.kt     # SearchResponse, LoadResponse, Episode, etc.
│       │       │   ├── TvType.kt     # TvType enum, Qualities, etc.
│       │       │   └── APIHolder.kt  # Singleton holder + APIRepository wrapper
│       │       ├── core/             # Cross-cutting infrastructure
│       │       │   ├── network/      # Ktor-based HTTP client (expect/actual)
│       │       │   └── storage/      # PlatformStorage (SharedPreferences / java.util.prefs)
│       │       ├── plugins/          # Plugin system
│       │       │   ├── BasePlugin.kt          # Plugin abstract class
│       │       │   ├── PluginManager.kt       # Loads .ws3/.jar files
│       │       │   ├── repository/            # RepositoryManager (repo JSON)
│       │       │   ├── stremio/               # StremioAddonClient
│       │       │   ├── js/                    # JsPluginRuntime (Rhino)
│       │       │   ├── extractors/            # StreamTape, MixDrop, Doodstream, M3U8
│       │       │   └── m3u8/                  # M3u8Helper (HLS parser)
│       │       ├── features/          # Feature modules
│       │       │   ├── home/           # HomeScreen + repository
│       │       │   ├── search/         # SearchScreen + debounced search
│       │       │   ├── details/        # DetailsScreen + episode list
│       │       │   ├── library/        # LibraryScreen (bookmarks/watched)
│       │       │   ├── downloads/      # DownloadsScreen
│       │       │   ├── player/         # PlayerScreen (expect/actual)
│       │       │   ├── settings/       # SettingsScreen
│       │       │   └── extensions/     # ExtensionsScreen
│       │       └── ui/                 # UI components + theme
│       │           ├── theme/          # WaveStreamTheme, Typography
│       │           └── components/     # PosterCard, WaveBottomBar
│       ├── androidMain/        # Android-specific impls
│       │   └── kotlin/com/wavestream/
│       │       ├── core/network/      # OkHttp engine
│       │       ├── core/storage/      # AndroidStorage (SharedPreferences)
│       │       ├── plugins/           # PathClassLoader-based plugin loading
│       │       └── features/player/   # ExoPlayer via Media3
│       └── desktopMain/        # Desktop (JVM) impls
│           └── kotlin/com/wavestream/
│               ├── main.kt            # Desktop entry point
│               ├── core/network/      # Java engine
│               ├── core/storage/      # DesktopStorage (java.util.prefs)
│               ├── plugins/           # URLClassLoader-based plugin loading
│               └── features/player/   # Desktop simulation (no ExoPlayer)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml       # Version catalog
├── gradlew, gradlew.bat
└── run.sh                       # Desktop run script
```

## Building

### Prerequisites

- JDK 17+ (for desktop)
- Android SDK (for Android builds)
- Gradle 9.4.1+ (wrapper included)

### Desktop (development/testing)

```bash
./run.sh
```

This compiles the Kotlin sources and launches the desktop app via `java -cp`.

### Android (production APK)

```bash
# Set up Android SDK
export ANDROID_HOME=/path/to/android/sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Build debug APK
./gradlew :app:assembleStableDebug

# Build release APK (needs signing config)
./gradlew :app:assembleStableRelease
```

The APK will be at `app/build/outputs/apk/stable/debug/app-stable-debug.apk`.

### Build variants

- `stable` — stable release
- `prerelease` — prerelease with `.prerelease` applicationId suffix

## Writing Extensions

### CloudStream-style provider (Kotlin)

```kotlin
import com.wavestream.api.*
import com.wavestream.plugins.BasePlugin
import com.wavestream.plugins.WavestreamPlugin

class MyProvider : MainAPI() {
    override var name = "MyProvider"
    override var mainUrl = "https://example.com"
    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse>? {
        // ... scrape your site or call your API
        return listOf(
            newMovieSearchResponse("Movie Title", "/movie/123") {
                posterUrl = "https://example.com/poster.jpg"
                year = 2024
            }
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        return newMovieLoadResponse("Movie Title", url, data = url) {
            plot = "A description."
            year = 2024
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        callback(newExtractorLink(
            source = name,
            name = "1080p",
            url = "https://cdn.example.com/movie.m3u8",
            type = ExtractorLinkType.M3U8,
        ) {
            this.quality = Qualities.P1080.value
        })
        return true
    }
}

@WavestreamPlugin(name = "MyProvider", version = 1)
class MyPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(MyProvider())
    }
}
```

Compile against `com.wavestream:shared` and package as a `.ws3` (zip with `manifest.json` + `classes.dex`).

### Stremio addon (no code needed)

Just add the manifest URL in the Extensions screen. The addon will be fetched and integrated automatically.

### JS plugin scraper (JavaScript)

```javascript
module.exports.getStreams = function(tmdbId, mediaType, season, episode) {
    var res = fetch("https://api.example.com/streams?tmdbId=" + tmdbId);
    var data = res.json();
    return data.streams;
};

module.exports.onSettings = function() {
    return [{ id: "apiKey", label: "API Key", type: "text" }];
};
```

The JS plugin has access to `fetch`, `URL`, `atob`/`btoa`, `console`, `cheerio` (planned), and `require` (planned). The runtime executes it via Mozilla Rhino in interpreted mode (no JIT, works on Android).

## License

MIT — see LICENSE file.

## Credits

- Architecture inspired by [CloudStream](https://github.com/recloudstream/cloudstream) (extension system)
- Stremio addon integration inspired by [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile)
- Built with Kotlin Multiplatform, Compose Multiplatform, Ktor, Media3, Coil, Rhino
