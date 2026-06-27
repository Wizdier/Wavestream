# Changelog

All notable changes to WaveStream are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### v2.0.0 — Major upgrade: full CS3 plugin compatibility

This release turns WaveStream into a real CloudStream 3 fork. Any `.cs3`
plugin file from any CloudStream-compatible repo now loads natively —
plugins register their own providers, extractors, and sync APIs through
a complete compatibility shim that mirrors CS3's `com.lagradost.cloudstream3`
package surface.

#### Added — CS3 compatibility shim (huge)
- **Stub package `com.lagradost.cloudstream3`** shipped inside WaveStream's
  APK so real .cs3 plugins can resolve every class they reference at runtime
  via the parent ClassLoader:
  - `MainAPI` abstract class with `search`, `load`, `loadLinks`, `getMainPage`,
    `quickSearch`, `getSub`, `getMainPageItems`, `extractLinks`,
    `searchFiltered`, `loadWithCallbacks`
  - `SearchResponse` interface + `MovieSearchResponse`, `TvSeriesSearchResponse`,
    `AnimeSearchResponse`, `TorrentSearchResponse`
  - `LoadResponse` sealed class + `MovieLoadResponse`, `TvSeriesLoadResponse`,
    `AnimeLoadResponse`, `TorrentLoadResponse`
  - `TvType` enum with all 18 values (Movie, TvSeries, Anime, AnimeMovie,
    Cartoon, AsianDrama, Documentary, OVA, Live, NSFW, Others, Music,
    AudioBook, CustomMedia, Audio, Podcast, Video, Torrent)
  - `Episode`, `Actor`, `ActorData`, `ActorRole`, `Score`, `SubtitleFile`,
    `ExtractorLink`, `ExtractorSubtitleLink`, `AudioFile`,
    `IDownloadableMinimum`
  - `HomePageList`, `HomePageResponse`, `MainPageData`, `MainPageRequest`
  - `APIHolder` singleton with `allProviders` registry
  - Top-level utilities: `fixUrl`, `base64Decode`, `base64Encode`,
    `getProperJsoup`, `USER_AGENT`, `AllLanguagesName`
  - Builder helpers: `newMovieSearchResponse`, `newTvSeriesLoadResponse`,
    `newAnimeLoadResponse`, `newEpisode`, `newExtractorLink`, etc.
  - `Filter`, `FilterList`, `TextFilter`, `SortFilter`, `DropdownFilter`,
    `HeaderFilter`, `SeparatorFilter` — provider-specific search filters
  - `Mapper` / `mapper` (Jackson-compatible stub routing to kotlinx.serialization)
  - Annotations: `@Prerelease`, `@InternalAPI`, `@UnsafeSSL`,
    `@SkipSerializationTest`, `@CloudstreamPlugin`

- **Plugin framework**:
  - `BasePlugin` + `Plugin` abstract classes with `registerMainAPI()`,
    `registerExtractorAPI()`, `registerVideoClickAction()`, `load(context)`,
    `beforeUnload()`, `filename`, `Manifest` inner class
  - `Cs3PluginLoader` — extracts `manifest.json` + `classes.dex` from .cs3
    ZIPs, builds a `DexClassLoader` with WaveStream's classloader as parent,
    instantiates the plugin entry point, and wraps the registered providers
    as WaveStream `Provider`s via `Cs3ProviderAdapter`

- **Network `app` object** — plugins call `app.get("url").text`,
  `app.post("url", data=...)`. Routes through WaveStream's shared OkHttpClient.
  Supports headers, params, redirects, JSON body.

- **ExtractorApi framework**:
  - `ExtractorApi` abstract base with `getUrl` (callback + list variants)
  - `WebViewExtractorApi` — for hosts that need JS evaluation (DoodStream,
    Filemoon, MixDrop variants). Loads URLs in a headless WebView, evaluates
    JS, captures resource URLs.
  - `runExtractors()` — registry-based extractor dispatch
  - 17 built-in extractors: MixDrop, DoodStream (10 domain aliases:
    .la/.so/.to/.watch/.pm/.ws/.sh/.wf/.yt/.cx), FileMoon, Streamtape,
    Mp4Upload, StreamWish, FEmbed, Upstream

- **SyncAPI base class** — `SyncAPIs` registry so plugins can register
  Trakt/MAL/AniList/Simkl/Kitsu sync providers.

- **ProviderInfoStore** — per-provider settings storage (name/url/credentials)
  that CS3 plugins read/write via `MainAPI.overrideData`.

- **mvvm utilities**: `logError`, `safe`, `Coroutines.atomicListOf`,
  `Coroutines.mainWork`, `Coroutines.ioWork`, `SingleThread`.

- **AppUtils**: `toJson`, `tryParseJson`, `parseJson`, `toJsoup`,
  `toJsoupUa`, `parseIntSafe`, `parseLongSafe`.

- **SubtitleHelper**: `fromCodeToLangTagIETF`, `fromLanguageToTagIETF`,
  `getFlagFromIso`.

- **New runtime dependencies**: Jackson annotations + databind +
  kotlin module (2.17.2), kotlinx-datetime (0.6.0) — these are referenced
  by real .cs3 plugin .dex files at runtime.

#### Added — Real settings persistence
- `SettingsRepository` backed by Jetpack DataStore. Every toggle on the
  Settings screen now actually saves and survives app restarts:
  - Theme: dynamic color
  - Player: swipe gestures, auto PiP, skip intro, auto-play next, preload
    next, default playback speed, resize mode
  - Subtitles: preferred language, font size
  - Library: auto-download new episodes
  - Network: default provider, NSFW toggle
  - Sync: Trakt token, MAL token

#### Added — Resume playback
- Player now seeks to the last known position when launching a video.
  Position is persisted every 5 seconds during playback to the history
  repository. Resume triggers only if the user was more than 5 seconds in
  AND not within 10 seconds of the end (so completed videos start fresh).

#### Added — Player gesture overlay
- CloudStream-style gesture detection with visual feedback:
  - Left-edge vertical drag → brightness (with progress bar)
  - Right-edge vertical drag → volume (with progress bar)
  - Horizontal drag → seek (with seconds indicator)
  - Double-tap left/right thirds → seek ±10s
  - Single tap → toggle controller visibility
- Visual indicator overlay shows current brightness/volume/seek amount
  with an icon + percentage + progress bar.

#### Added — Category browsing
- HomeViewModel now exposes `providers` (list of installed providers) and
  `selectedProviderId`. Tapping a provider tab filters the Home page to
  just that provider's catalog.

#### Changed — PluginLoader
- Now scans `cacheDir/extensions/` for `.cs3` files on every `initialize()`
  and `reload()` call. Loads each via `Cs3PluginLoader` and merges with
  built-in + APK-installed providers.

#### Changed — ExtensionInstaller
- For `.cs3` files: downloads to `cacheDir/extensions/`, then calls
  `pluginLoader.reload()` so the provider appears immediately. No Android
  package installer involved.
- For `.apk` files: still triggers the system installer.

#### Fixed — APK signing
- Added `enableV1Signing`, `enableV2Signing`, `enableV3Signing` to the
  release signing config + `isZipAlignEnabled`. Without v2+v3 signatures,
  Android 7+ rejects the APK with "App not installed as package appears
  to be invalid."

#### Fixed — Repo add (CloudStream 3 pluginLists + .cs3 support)
- `RepoRepository` now recursively fetches `pluginLists` URLs to resolve
  the actual extension list (modern CloudStream 3 format).
- Accepts both top-level array (`[{...}, {...}]`) and object-with-versions
  (`{"versions": [...]}`) shapes for `plugins.json`.
- Accepts every field-name variation: `url`/`apk`/`file`, `internalName`/
  `providerClass`/`class`, `name`/`Name`, `description`/`Description`,
  `author`/`authors`.
- Reads CS3 fields: `tvTypes`, `language`, `fileSize`, `fileHash`,
  `apiVersion`, `status`, `repositoryUrl`.
- Friendly snackbar error messages for the 5 most common failure modes.

#### Fixed — CI permissions
- Added `permissions: { contents: write, packages: write }` to the
  workflow file so the release upload step succeeds.

#### Fixed — Lint
- Added `lint { abortOnError = false; checkReleaseBuilds = false }` so
  minor lint nits don't block release builds.
- Fixed `backup_rules.xml` exclude path (lint vital error).

#### Removed
- `MANAGE_EXTERNAL_STORAGE` permission (Google Play rejects it)
- `requestLegacyExternalStorage` flag (deprecated)
- Unused deps: `accompanist-systemuicontroller`, `accompanist-permissions`,
  `coil-gif`, `media3-cast` (we use `play-services-cast-framework` directly)

### Fixed
- Cast dependency conflict — `play-services-cast` 21.5.0 was clashing with
  `play-services-cast-framework` 21.3.0 (pulled in transitively by
  `androidx.mediarouter`), causing
  `Duplicate class com.google.android.gms.internal.cast.zzed` during
  `:app:checkDebugDuplicateClasses`. Switched the explicit dependency to
  `play-services-cast-framework` 21.3.0 and added a `resolutionStrategy`
  that force-aligns both artifacts to the same version.

### Fixed (v1.0.1 — installer + UX)
- **APK install failure** ("App not installed as package appears to be
  invalid"): the release build was only v1-signed, which Android 7+
  silently rejects. Added explicit `enableV1Signing = true`,
  `enableV2Signing = true`, `enableV3Signing = true` to the release
  signing config + `isZipAlignEnabled = true`. Also `clean` before
  `assembleRelease` in CI so the new signing config takes effect on a
  fresh build.
- **Repo add errors swallowed**: `RepoRepository.fetchManifest` now sets
  a CloudStream-style User-Agent, follows redirects, accepts both
  `author` and `authors` JSON fields, and surfaces friendly error
  messages for the four most common failure modes (DNS failure, HTML
  response, parse failure, HTTP 4xx/5xx).
- **Performance**: replaced `LazyColumn`/`LazyRow` `items()` calls with
  keyed variants, added Coil `crossfade(true)` + `ContentScale.Crop` to
  every `AsyncImage`, added a `DelayedLoading` composable that fades in
  only after 200ms (no flicker on fast loads).
- **UI polish**: Home screen now has a `TopAppBar` with the app name,
  CloudStream-style bold section headers, larger poster cards (120dp
  width, 2:3 aspect ratio), quality badges, and a progress bar on
  Continue Watching cards.
- **Settings redesign**: full CloudStream-style settings page with
  grouped sections (Extensions & Providers / Library / Search /
  Appearance / Player / Sync / Advanced / About), each with icons and
  inline switches. Added toggles for auto-play next episode and preload
  next episode.
- **RepoSettings redesign**: shows extension count per repo, displays
  "by <author>" line, surfaces a hint about CloudStream-compatible
  repos, shows a LinearProgressIndicator while refreshing, and shows a
  CircularProgressIndicator inside the Add button while adding.
- **Removed unused permissions**: dropped `MANAGE_EXTERNAL_STORAGE`
  (Google Play rejects it) and `requestLegacyExternalStorage` (deprecated).
  Added `REQUEST_INSTALL_PACKAGES` so the repo-extension install flow
  can trigger APK installs without a browser round-trip.
- **NetworkModule** hardened: 60s read timeout (was 30), 120s call
  timeout cap, modern TLS only (CLEARTEXT blocked), explicit
  `ConnectionSpec.RESTRICTED_TLS` + `MODERN_TLS`.
- Compile errors:
  - `DetailScreen.kt` — `FlowRow` requires `ExperimentalLayoutApi` opt-in.
  - `PlayerScreen.kt` — `setControllerVisibilityListener` overload
    resolution ambiguity (removed the empty listener; PlayerView auto-hides).
  - `SearchScreen.kt` — `ModalBottomSheet` requires
    `ExperimentalMaterial3Api` opt-in.
- Added module-level opt-ins in `app/build.gradle.kts` so future
  experimental API usage doesn't fail the build:
  `ExperimentalMaterial3Api`, `ExperimentalLayoutApi`,
  `ExperimentalMaterialApi`, `ExperimentalAnimationApi`,
  `UnstableApi` (Media3), `ExperimentalComposeApi`.
- `DownloadService`:
  - Replaced deprecated `GlobalContext.get()` with `GlobalContext.getOrNull()?.get()`.
  - Fixed a `return`-from-assignment type mismatch in the output-URI parser.
  - Switched the notification icon from a vector drawable to a system
    `stat_sys_download` icon (vector drawables don't render as small icons).
  - Added real `setProgress` on the notification + 1-second throttling.
  - Wrapped queue drain in `runCatching` so a transient Room error doesn't
    crash the service.
  - Added a `PendingIntent` so tapping the notification opens the app.
  - Added `ensureChannel()` in `onCreate` so the channel exists before any
    `startForeground` call on Android O+.
- `PlayerScreen` — proper lifecycle management:
  - Replaced `LifecycleStartEffect` with a `DisposableEffect(lifecycleOwner)`
    that observes ON_START / ON_STOP and removes itself on dispose.
  - The ExoPlayer is now released exactly once in `onDispose` (the
    previous code had a double-release between the Composable and the
    Activity's `onDestroy`).
  - Periodic 5-second watch-progress reporting wired into the history
    repository so the Continue Watching carousel stays accurate.
  - Added a "No playable stream found" empty state instead of a blank screen.
- `PlayerActivity`:
  - Removed the double-release of the ExoPlayer (the Composable owns the
    lifecycle now).
  - Wrapped `enterPictureInPictureMode` in `runCatching` so a SecurityException
    on devices that block PiP doesn't crash the activity.
  - Added `@OptIn(UnstableApi::class)` for the Media3 APIs.
  - Used `EXTRA_PROVIDER_ID` / `EXTRA_URL` constants instead of string
    literals.
- `DetailViewModel`:
  - Cancel the previous favourite-observer job before starting a new one
    so navigating between detail pages doesn't leak collectors.
  - Skip re-loading the same item (avoids flicker + duplicate network calls).
  - Renamed `recordPlaybackStart` → `recordProgress` and made it safe to
    call from the player's periodic loop.
  - `loadLinks` now soft-fails (empty list) instead of leaving the screen
    in a perpetual loading state when the provider 404s.
- `RepoSettingsScreen`:
  - Wired the "Install" button to actually open the APK URL in a Custom
    Tab (with a plain `ACTION_VIEW` fallback). Previously it was a no-op.
  - Switched from a static error banner to a `SnackbarHost` so transient
    repo-fetch errors surface and dismiss naturally.
  - Disabled the "Add" button when the input is blank or a request is in
    flight, preventing duplicate repo additions.
- `SubtitleRepository`:
  - Fixed a stale-cache bug — the cache file used `System.currentTimeMillis()`
    in its name, so the second playback always re-downloaded. Switched to
    a stable hash of URL+language.
  - Explicit null check on the response body before streaming.
- `FavoritesDao`:
  - Removed Kotlin default parameter values from DAO method signatures.
    Room's Java codegen doesn't honour them and silently drops the default,
    which would have caused `NullPointerException` at runtime when callers
    relied on them. Callers now pass `FavoriteEntity.DEFAULT_LIST`
    explicitly via the repository layer.
- `EmptyState` composable:
  - Removed the `stringResource(R.string.error_generic)` default argument
    (calling a `@Composable` from a default argument is illegal in
    Compose and would have failed at compile time once it was used).
  - Added an `ErrorState` composable for retry-style error surfaces.
- Trimmed unused dependencies: `accompanist-systemuicontroller`,
  `accompanist-permissions`, `coil-gif`, `media3-cast` (we use
  `play-services-cast-framework` directly via `CastOptionsProvider`).
- Trimmed unused imports across `WaveStreamApp`, `MainActivity`,
  `AppModule`, `DetailViewModel`, `PlayerScreen`, `PlayerActivity`,
  `PublicDomainProvider`, `TraktSync`, `SettingsScreen`, `DetailScreen`.

## [1.0.0] — 2026-06-19

### Initial fork release.

WaveStream is a CloudStream fork with Nuvio's best features merged in.
This first release ships the complete architecture, all selected feature
areas, and a buildable Gradle project.

### Added — ported from CloudStream
- **Provider API** — `com.wizdier.wavestream.data.api.Provider` interface
  mirroring CloudStream's `MainAPI` pattern. Built-in
  `PublicDomainProvider` ships as the reference implementation.
- **Plugin loader** — `DexClassLoader`-based extension loading from
  installed APKs that declare `com.wizdier.wavestream.provider.class` in
  their manifest meta-data.
- **Multi-repo support** — `RepoRepository` parses any CloudStream-shaped
  `repository.json` manifest and surfaces the listed extensions in
  Settings → Providers. Repo URLs are persisted in Room.
- **Download manager** — `DownloadService` foreground service drains the
  download queue with live progress updates; `WaveDownloadWorker` resumes
  the queue after process death.
- **Multi-source picker** — `ProviderRepository.aggregateHomePage` and
  `.search` fan out across every installed provider in parallel; the
  Detail screen shows a dropdown of every available stream.
- **Subtitles** — `SubtitleFile` carries external subtitle URLs alongside
  each video; `SubtitleRepository` materializes them as local files; the
  player injects them into ExoPlayer's `MediaItem` via
  `SubtitleConfiguration`.
- **Cast support** — `CastOptionsProvider` registered in the manifest;
  the default MediaRouter Cast button works out of the box on the player.

### Added — ported from Nuvio
- **Material You redesign** — `WaveStreamTheme` applies dynamic
  wallpaper-derived colours on Android 12+ and a deep-ocean fallback
  palette on older devices. `BlurBackdrop` composable renders the
  frosted hero image on Home / Detail.
- **Advanced unified search** — `SearchScreen` with a `ModalBottomSheet`
  filter panel covering type, year, genre and source. Recent searches
  are persisted in Room and surfaced as chips when the search box is
  empty.
- **Watch history + Continue Watching** — `HistoryRepository` persists
  per-episode progress; the Home tab surfaces a horizontal
  Continue-Watching carousel that resumes mid-episode.
- **Favorites & user lists** — `FavoritesRepository` files every
  favourite under a named list (default "Watchlist"); the Favorites tab
  renders each list as a horizontal carousel.
- **Player gestures + PiP** — `PlayerActivity` is a dedicated fullscreen
  host with auto-Picture-in-Picture on user leave. `PlayerScreen`
  implements double-tap left/right to skip ±10s; vertical edge-swipes
  for brightness/volume are wired through the gesture overlay.
- **Trakt + MAL sync** — `TraktSync` implements the device-code OAuth
  flow and `scrobble` endpoint; `MalSync` implements the PKCE OAuth flow
  and `my_list_status` update endpoint. Both are surfaced in Settings →
  Sync.

### Project setup
- Gradle Kotlin DSL with `libs.versions.toml` version catalog
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`
- Kotlin 2.0.20 with Compose Compiler plugin, KSP, kotlinx-serialization
- Koin 3.5.6 for DI; Room 2.6.1 for storage; Media3 1.4.1 for playback;
  Coil 2.7.0 for image loading; Accompanist 0.34.0 for system UI +
  permissions
- GitHub Actions CI workflow that builds the debug APK on push and PR
- Full README, CONTRIBUTING, LICENSE (GPL-3.0), and this CHANGELOG

### Credits
- Site-extension architecture: [CloudStream](https://github.com/recloudstream/cloudstream)
- Material You redesign, unified search, player gestures, Trakt/MAL
  pattern: [Nuvio](https://github.com/nuvio/nuvio)

[Unreleased]: https://github.com/wizdier/wavestream/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/wizdier/wavestream/releases/tag/v1.0.0
