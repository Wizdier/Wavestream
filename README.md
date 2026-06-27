# WaveStream 🌊

> Surf the wave of streaming — a CloudStream fork with Nuvio's best features merged in.

[![Build](https://github.com/wizdier/wavestream/actions/workflows/build.yml/badge.svg)](https://github.com/wizdier/wavestream/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-orange.svg)](https://kotlinlang.org)

WaveStream is a media-browsing and playback app for Android that combines
the **plugin-first site-extension architecture** of
[CloudStream](https://github.com/recloudstream/cloudstream) with the
**Material You redesign, advanced unified search, and player UX** of
[Nuvio](https://github.com/nuvio/nuvio). It is not a content source itself
— it loads content from provider extensions you install from any community
repo URL.

> **Based on CloudStream & Nuvio** — thank you to all upstream contributors.
> See the [Credits](#-credits) section for full attribution.

---

## ✨ Features

### From CloudStream (the core engine)
- 🧩 **Provider API** — `MainAPI`-style Kotlin plugin interface with multi-repo installation. Add any CloudStream-compatible repo URL and its extensions show up in the source picker.
- 📥 **Download Manager** — foreground-service downloader with progress notifications, pause / resume / cancel, queued by Room.
- 🎬 **Multi-source picker** — every title lists every provider that has it, with quality and server chips per stream.
- 💬 **Subtitles** — external `.srt` / `.vtt` / `.ass` loading, side-loaded into ExoPlayer per stream.
- 📺 **Cast support** — standard Cast button on the player; fling any stream to any Chromecast on the network.

### From Nuvio (the UX layer)
- 🎨 **Material You** — dynamic wallpaper-derived colours on Android 12+, deep-ocean fallback palette on older devices, frosted-blur backdrops on Home / Detail.
- 🔍 **Advanced unified search** — search across movies, series, anime and documentaries at once; filter by type, year, genre, source.
- ⏯ **Watch history + Continue Watching** — per-episode progress, resume from where you left off, recently-watched chips on Home.
- ⭐ **Favorites & user lists** — file any title under a named list ("Watchlist", "Anime backlog", …); each list is a horizontal carousel on the Favorites tab.
- 📱 **Player swipes & PiP** — vertical left-edge drag for brightness, right-edge for volume, horizontal drag to seek, double-tap left/right to skip ±10s, auto Picture-in-Picture on home press.
- 🔄 **Trakt + MyAnimeList sync** — push watch progress and favourites to Trakt and MAL so your library travels with you.

---

## 📸 Screenshots

> _Placeholder — drop your screenshots into `docs/screenshots/` and update this section._

| Home | Search | Detail | Player |
|------|--------|--------|--------|
| ![Home](docs/screenshots/home.png) | ![Search](docs/screenshots/search.png) | ![Detail](docs/screenshots/detail.png) | ![Player](docs/screenshots/player.png) |

---

## 🚀 Getting started

### Prerequisites
- Android Studio Koala / Ladybug or newer
- JDK 17
- Android SDK 34 (compileSdk)
- An Android device or emulator running Android 8.0 (API 26) or newer

### Build
```bash
git clone https://github.com/wizdier/wavestream.git
cd wavestream
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

> **First-time bootstrap** — this repo ships the Gradle wrapper scripts
> (`gradlew`, `gradlew.bat`) and the wrapper properties file
> (`gradle/wrapper/gradle-wrapper.properties`), but **not** the wrapper
> binary jar (it's a binary file that doesn't diff cleanly). The first
> time you build, either:
> - open the project in Android Studio (it will download the wrapper jar automatically), or
> - run `gradle wrapper --gradle-version 8.9` once if you have Gradle installed system-wide,
> - or just run `gradle :app:assembleDebug` directly.
>
> The CI workflow bootstraps the wrapper jar automatically on every run.

### Sideload
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Configure sync credentials (optional)
Before publishing your own build, register a Trakt and MyAnimeList
application and paste your client id / secret into
`app/src/main/res/values/strings.xml`:

```xml
<string name="trakt_client_id">YOUR_TRAKT_CLIENT_ID</string>
<string name="trakt_client_secret">YOUR_TRAKT_CLIENT_SECRET</string>
<string name="mal_client_id">YOUR_MAL_CLIENT_ID</string>
```

The app ships with a built-in **Public Domain Movies** provider so it's
immediately usable out of the box — real content sources are installed
from community repos via **Settings → Providers → Add repository**.

---

## 🧩 Adding providers

WaveStream reads any JSON manifest that follows this shape (compatible
with CloudStream's `repository.json`):

```json
{
  "name": "My WaveStream Repo",
  "description": "A curated collection of providers",
  "author": "you",
  "requiresApi": 1,
  "versions": [
    {
      "name": "ExampleProvider",
      "version": "1.0.0",
      "apk": "https://example.com/exampleprovider-1.0.0.apk",
      "providerClass": "com.example.provider.ExampleProvider",
      "description": "Streaming from example.com"
    }
  ]
}
```

Provider APKs are loaded with a `DexClassLoader` and must declare the
fully-qualified class name of a `com.wizdier.wavestream.data.api.Provider`
implementation under the `com.wizdier.wavestream.provider.class`
`<meta-data>` key in their `AndroidManifest.xml`.

See
[`PublicDomainProvider.kt`](app/src/main/java/com/wizdier/wavestream/data/api/PublicDomainProvider.kt)
for a reference implementation.

---

## 🏗️ Architecture

```
app/src/main/java/com/wizdier/wavestream/
├── WaveStreamApp.kt            # Application — Koin DI + WorkManager + channels
├── MainActivity.kt             # Compose host
├── data/
│   ├── api/                    # Provider interface + DTOs + built-in provider
│   ├── db/                     # Room entities, DAOs, database
│   ├── network/                # Shared OkHttp client
│   ├── plugin/                 # DexClassLoader-based provider loader
│   ├── repository/             # Provider / History / Favorites / Download / Subtitle / Repo
│   └── sync/                   # Trakt + MyAnimeList clients
├── di/                         # Koin module
├── services/                   # DownloadService + WaveDownloadWorker
├── ui/
│   ├── theme/                  # Material You colors, type, shapes, theme
│   ├── components/             # Reusable cards, backdrops, states
│   ├── navigation/             # NavHost + routes
│   ├── home/                   # Home (Continue Watching + provider lists)
│   ├── search/                 # Unified search with filter sheet
│   ├── detail/                 # Title detail with source picker
│   ├── player/                 # ExoPlayer + gestures + PiP + subtitles
│   ├── downloads/              # Download queue UI
│   ├── favorites/              # User-lists UI
│   ├── settings/               # Main settings + Repos + Sync + About/Credits
│   └── cast/                   # CastOptionsProvider
└── util/                       # Helpers
```

### Tech stack
- **Kotlin 2.0.20** with coroutines + serialization
- **Jetpack Compose** (Material 3, BOM 2024.09)
- **Media3 / ExoPlayer 1.4** for playback, casting, and subtitles
- **Room** for the local database (history, favorites, downloads, repos, search history)
- **Koin** for DI
- **OkHttp + Retrofit + Jsoup** for provider scraping
- **WorkManager** for resilient download wake-ups
- **Coil** for image loading
- **Accompanist** for system UI controller + permissions

---

## 🤝 Contributing

Pull requests are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for the
full guide. Quick start:

1. Fork & branch from `main`
2. Make your change with tests where reasonable
3. Run `./gradlew :app:assembleDebug` to confirm the build
4. Open a PR describing **what** and **why**

Please be respectful of upstream licenses (GPL-3.0) when porting code from
CloudStream, Nuvio, or their providers.

---

## 🚀 Releasing APKs

Releases are fully automated via GitHub Actions. Tagging a commit with
`v*` triggers a build that:

1. Decodes the signing keystore from the `KEYSTORE_BASE64` repository secret.
2. Builds a signed `app-release.apk`.
3. Generates a `sha256` checksum alongside it.
4. Publishes a GitHub Release with both files attached, using the
   `CHANGELOG.md` contents as the release body.

### One-time setup

Generate a keystore locally (keep this file safe — the same keystore must
sign every future release or users won't be able to update in place):

```bash
keytool -genkey -v -keystore wavestream.keystore \
  -alias wavestream -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 wavestream.keystore > keystore.b64
```

Add these repository secrets at
`Settings → Secrets and variables → Actions`:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64`   | contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS`         | `wavestream` |
| `KEY_PASSWORD`      | key password |

**Never commit `wavestream.keystore` or `keystore.b64`** — they're in
`.gitignore` already.

### Publishing a release

```bash
# Update versionCode / versionName in app/build.gradle.kts first
git commit -am "Bump to v1.1.0"
git tag -a v1.1.0 -m "WaveStream v1.1.0"
git push origin v1.1.0
```

GitHub Actions takes it from there. The release appears at
`https://github.com/wizdier/wavestream/releases/tag/v1.1.0` within a
few minutes, signed APK attached.

### Manual release (fallback)

If CI is broken and you need to ship immediately:

```bash
export KEYSTORE_FILE=$PWD/wavestream.keystore
export KEYSTORE_PASSWORD='...'
export KEY_ALIAS=wavestream
export KEY_PASSWORD='...'
./gradlew :app:assembleRelease
# Then draft a release on GitHub and attach
# app/build/outputs/apk/release/app-release.apk
```

---

## 📜 License

WaveStream is released under the **GPL-3.0** license — see
[LICENSE](LICENSE). Upstream code ported from CloudStream and Nuvio
remains under their original licenses (also GPL-3.0).

## 🙏 Credits

WaveStream would not exist without these projects and their communities:

- **[CloudStream](https://github.com/recloudstream/cloudstream)** — the
  site-extension architecture, `MainAPI` plugin pattern, download manager,
  and multi-source picker. WaveStream's `Provider` interface is a
  streamlined port of CloudStream's `MainAPI`.
- **[Nuvio](https://github.com/nuvio/nuvio)** — the Material You redesign,
  advanced unified search UX, player gesture system, and Trakt/MAL sync
  pattern.
- Every provider author in the CloudStream / Nuvio community whose repo
  JSON manifests WaveStream can consume as-is.

If you port a feature from either upstream, please credit them in your
commit message and in the relevant source file.

---

## ⚠️ Disclaimer

WaveStream is a media-browsing client. It does not host, stream or
distribute any content. All content is fetched by user-installed provider
extensions from third-party sources. The WaveStream maintainers are not
responsible for what users choose to install or watch. Use only with
content you have the legal right to access in your jurisdiction.
