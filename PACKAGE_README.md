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
├── .github/workflows/build.yml  # CI: desktop JVM + Android APK
│
├── library/                   # Patched CloudStream 3 library module
├── shared/                    # Compose Multiplatform UI module
├── app/                       # Android application entry point
│
└── dist/
    ├── Wavestream-linux-x64-1.0.0.jar  # Prebuilt desktop uber jar (Linux x64, 87 MB)
    └── Wavestream-1.0.0-debug.apk      # Prebuilt Android debug APK (30 MB)
```

## Quickstart — Run the Desktop App

You need Java 17 or later installed on your system.

```bash
java -jar dist/Wavestream-linux-x64-1.0.0.jar
```

On first launch, the app creates `~/.wavestream/` with subdirectories:
- `Extensions/` — drop `.cs3` (Android) or `.jar` (Desktop) CloudStream plugins here
- `Downloads/` — placeholder for downloaded videos
- `preferences.json` — JSON-file preferences store

## Quickstart — Install the Android APK

```bash
adb install dist/Wavestream-1.0.0-debug.apk
```

Or copy the APK to your Android device and tap it in a file manager (enable "Install from unknown sources" first).

The APK targets:
- `minSdk = 26` (Android 8.0) — required because Rhino 1.9.1 uses `MethodHandle.invoke`
- `targetSdk = 35` (Android 15)
- `applicationId = com.wavestream.app`

## Quickstart — Build From Source

### Desktop verification (canonical)

```bash
chmod +x ./gradlew        # only needed once after extracting the zip
./gradlew :library:compileKotlinDesktop :shared:compileKotlinDesktop
```

This is the build target from the original reproduction guide. It compiles the patched CloudStream library and the entire Compose Multiplatform UI against the JVM target.

> **Tip:** If you see `./gradlew: Permission denied`, run `chmod +x ./gradlew`. The GitHub Actions workflow already does this automatically.

### Run desktop UI from source

```bash
./gradlew :shared:run
```

### Build desktop distribution

```bash
./gradlew :shared:packageUberJarForCurrentOS        # standalone jar
./gradlew :shared:packageDistributionForCurrentOS   # native installer (dmg/msi/deb)
```

### Build Android APK

You need the Android SDK with `platform-35` and `build-tools;35.0.0` installed. Copy `local.properties.template` to `local.properties` and set `sdk.dir`.

```bash
cp local.properties.template local.properties
# Edit local.properties to point to your Android SDK
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Pushing to Your GitHub

See [GITHUB_PUSH.md](GITHUB_PUSH.md) for three push options (HTTPS+PAT, gh CLI, SSH). The repo is already git-initialized with the initial commit and `origin` pointing to `https://github.com/wizdier/wavestream.git`.

## Loading Real CloudStream Plugins

To verify the plugin loader works with real CloudStream extensions:

1. Download a real plugin, e.g.:
   ```bash
   curl -L -o ~/.wavestream/Extensions/DailymotionProvider.jar \
     https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.jar
   ```
2. Restart the app (or click the rescan button in Extensions tab).
3. The Extensions tab should list `DailymotionProvider`.
4. Search "test" — Dailymotion results should appear.

## Verification Status

All targets now build successfully:

- ✅ `./gradlew :library:compileKotlinDesktop` — passes
- ✅ `./gradlew :shared:compileKotlinDesktop` — passes (canonical target from the reproduction guide)
- ✅ `./gradlew :shared:desktopJar` — passes
- ✅ `./gradlew :shared:packageUberJarForCurrentOS` — passes (this jar is in `dist/`)
- ✅ `./gradlew :app:assembleDebug` — passes (this APK is in `dist/`)
- ⚠️ `./gradlew :shared:packageDistributionForCurrentOS` — requires full JDK with `jlink`; not run in the build environment

## Build Environment Used

- Java: OpenJDK 21.0.11 (JRE only — native dist build needs full JDK)
- Gradle: 8.10.2 (via wrapper)
- Kotlin: 2.2.20 (auto-downloaded by Gradle)
- Compose Multiplatform: 1.8.0
- Android SDK: `platform-35` + `build-tools;35.0.0`
- OS: Debian 13 (Linux x64)

## Recent Fixes (this version)

If you previously tried building and hit errors, this version fixes:

1. **`./gradlew: Permission denied` in CI** — added `chmod +x ./gradlew` step to both CI jobs.
2. **`MethodHandle.invoke ... only supported starting with Android O (--min-api 26)`** — Rhino 1.9.1 requires API 26. Bumped `minSdk` from 21 to 26 in all three modules.
3. **`Unresolved reference 'initPlatform'` in MainActivity.kt** — fixed import from `com.wavestream.initPlatform` to `com.wavestream.platform.initPlatform`.
4. **`2 files found with path 'META-INF/versions/9/OSGI-INF/MANIFEST.MF'`** — added comprehensive `packaging.resources.excludes` block to `app/build.gradle.kts` covering OSGi manifests, signing files, kotlin_module files, and other common duplicate META-INF paths.
