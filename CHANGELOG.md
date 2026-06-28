# Changelog

All notable changes to WaveStream are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- OCR-based subtitle extraction for embedded streams
- Per-source quality preferences persisted across sessions
- DLNA / UPnP playback in addition to Cast
- Backup & restore of database + preferences

### Fixed
- Cast dependency conflict — `play-services-cast` 21.5.0 was clashing with
  `play-services-cast-framework` 21.3.0 (pulled in transitively by
  `androidx.mediarouter`), causing
  `Duplicate class com.google.android.gms.internal.cast.zzed` during
  `:app:checkDebugDuplicateClasses`. Switched the explicit dependency to
  `play-services-cast-framework` 21.3.0 and added a `resolutionStrategy`
  that force-aligns both artifacts to the same version.
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
