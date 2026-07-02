<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="128" alt="WaveStream logo"/>
</p>

<h1 align="center">WaveStream</h1>

<p align="center"><b>Ride the wave. Stream everything.</b></p>

**⚠️ Warning: By default, this app doesn't provide any video sources; you have to install extensions to add functionality to the app.**

WaveStream is a fork of [CloudStream](https://github.com/recloudstream/cloudstream) — the same powerful, extension-based media center, reskinned with a fresh ocean look.

## WaveStream exclusives

+ **Built-in MPV & VLC engines** — Settings → Player → *Playback engine*: choose ExoPlayer (default), MPV (libmpv) or VLC (libVLC) as the engine that plays videos **inside the app** — same UI, same gestures, same subtitles picker, no external apps needed. Falls back to ExoPlayer automatically if an engine fails. (MPV requires Android 8+.)
+ **Deep Ocean theme** — a new immersive dark-navy default theme tuned for OLED and long viewing sessions (the full original theme list is still available)
+ **Edge-to-edge immersive UI** — the app now draws into display cutouts/notches for a truly full-screen experience
+ **Leaner memory profile** — image caches are released automatically under memory pressure, keeping low-RAM phones and TV boxes smooth
+ **Hardened crash recovery** — the crash handler can no longer crash itself; the app restarts cleanly instead of looping
+ **Faster builds** — parallel Gradle + incremental Kotlin enabled out of the box

## Features

+ **100% CloudStream-compatible** — all existing CloudStream extensions (`.cs3` plugins) and repositories work out of the box
+ Stream and download Movies, TV series, Anime, Live TV and more
+ No ads, no tracking, no account required
+ Extension/plugin system with community repositories
+ Chromecast + FCast support
+ Tracker sync: MyAnimeList, AniList, Simkl
+ Subtitle support (OpenSubtitles, SubDL, SubSource, Addic7ed) with full styling
+ Built-in ExoPlayer with gestures, PiP, speed control, external player support (VLC, mpv, …)
+ Phone, tablet and Android TV layouts
+ Downloads with queue management and offline playback
+ Backup & restore, multiple user profiles
+ In-app updates from this repository ([Wizdier/WaveStream](https://github.com/Wizdier/WaveStream))

## What's different from CloudStream?

WaveStream is intentionally a **skin-level fork**:

| | CloudStream | WaveStream |
|---|---|---|
| App name | CloudStream | WaveStream |
| Package ID | `com.lagradost.cloudstream3` | `com.wizdier.wavestream` (installs side-by-side!) |
| Icon | Blue cloud | Teal/cyan waves |
| Theme accent | Blue `#3d50fa` | Ocean cyan `#00B8C4` / `#22D3EE` |
| Updates from | recloudstream/cloudstream | Wizdier/WaveStream |
| Extension API | `com.lagradost.cloudstream3` | **unchanged** (full compatibility) |

Because the internal Kotlin package is untouched, every CloudStream plugin loads and runs in WaveStream without modification.

## Installation

1. Download the latest APK from [Releases](https://github.com/Wizdier/WaveStream/releases).
2. Install it (allow unknown sources if prompted).
3. Add an extension repository: **Settings → Extensions → Add repository**, or follow the [CloudStream docs](https://recloudstream.github.io/csdocs/) — all repos work identically here.
4. Install extensions, pick a provider on the Home tab, and enjoy.

## Building from source

```bash
git clone https://github.com/Wizdier/WaveStream.git
cd WaveStream
./gradlew assembleStableDebug
```

Requirements: JDK 17, Android SDK (compileSdk per `gradle/libs.versions.toml`).

Optional API keys (for tracker login) go into `local.properties`:

```properties
simkl.id=...
simkl.secret=...
mal.key=...
anilist.key=...
```

## Extension development

Extensions are developed exactly as for CloudStream — see the
[official docs](https://recloudstream.github.io/csdocs/devs/gettingstarted/).
The plugin API package remains `com.lagradost.cloudstream3`, so any plugin built
for CloudStream is a WaveStream plugin too.

## DMCA / Copyright

WaveStream does not host, upload or manage any videos, films or content. It has no
video sources built in; it is a client that renders content from extensions installed
by the user, functioning like a search engine. Please do not create or use extensions
that infringe copyright.

## Credits & License

WaveStream is based on [CloudStream](https://github.com/recloudstream/cloudstream) by the
recloudstream team and contributors. Licensed under the
[GNU General Public License v3.0](LICENSE) — this fork keeps the same license, and its
full source is available in this repository.
