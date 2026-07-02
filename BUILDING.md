# Building WaveStream

## Option A — Let GitHub build it for you (recommended)

1. Create a new **public or private repo** named `WaveStream` under your account (`Wizdier`).
2. Upload the contents of this zip (or push with git):
   ```bash
   git init
   git add .
   git commit -m "WaveStream initial import"
   git branch -M master
   git remote add origin https://github.com/Wizdier/WaveStream.git
   git push -u origin master
   ```
3. **Debug APK** — every push to `master` runs the *Debug build* workflow.
   Get the APK from: repo → Actions → latest run → Artifacts → `WaveStream-debug`.
4. **Release APK** — tag a version and push it:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   The *Release build* workflow builds the APK and publishes it on the Releases page
   automatically. The in-app updater checks `Wizdier/WaveStream` releases, so users
   will be offered new versions automatically.

### Optional signing secrets (for release builds)
Unsigned release APKs can't be installed directly. Either install the debug APK, or add
these repository secrets (Settings → Secrets and variables → Actions) to have CI sign
the release:

| Secret | Meaning |
|---|---|
| `SIGNING_KEYSTORE_B64` | Your keystore.jks encoded with `base64 -w0 keystore.jks` |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Create a keystore once with:
```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias wavestream
```

### Optional tracker API secrets
`SIMKL_CLIENT_ID`, `SIMKL_CLIENT_SECRET`, `MAL_KEY`, `ANILIST_KEY` — only needed if you
want MAL/AniList/Simkl login to work in your builds.

## Option B — Build locally

Requirements: JDK 17 + Android SDK.

```bash
./gradlew assembleStableDebug      # debug APK
./gradlew assembleStableRelease    # unsigned release APK
```
APKs land in `app/build/outputs/apk/stable/`.

## What's inside (feature summary)

- Full CloudStream feature set, rebranded as WaveStream (`com.wizdier.wavestream`)
- 100% compatible with all CloudStream extensions/repositories
- Built-in playback engines: ExoPlayer (default), MPV (libmpv) and VLC (libVLC),
  selectable in Settings → Player → Playback engine
- Deep Ocean default theme + edge-to-edge immersive UI
- In-app updater pointed at github.com/Wizdier/WaveStream releases
