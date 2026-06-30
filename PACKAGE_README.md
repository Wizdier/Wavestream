# Wavestream — Distribution Package

This package contains the complete Wavestream project source plus a prebuilt desktop jar.

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
    └── Wavestream-linux-x64-1.0.0.jar  # Prebuilt desktop uber jar (Linux x64)
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

## Quickstart — Build From Source

### Desktop verification (canonical)

```bash
./gradlew :library:compileKotlinDesktop :shared:compileKotlinDesktop
```

This is the build target from the original reproduction guide. It compiles the patched CloudStream library and the entire Compose Multiplatform UI against the JVM target.

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

- ✅ `./gradlew :library:compileKotlinDesktop` — passes
- ✅ `./gradlew :shared:compileKotlinDesktop` — passes (the canonical target from the reproduction guide)
- ✅ `./gradlew :shared:desktopJar` — passes
- ✅ `./gradlew :shared:packageUberJarForCurrentOS` — passes (this jar is in `dist/`)
- ⚠️ `./gradlew :shared:packageDistributionForCurrentOS` — requires full JDK (jlink); not run in the build environment
- ⚠️ `./gradlew :app:assembleDebug` — requires Android SDK; not run in the build environment. The CI workflow in `.github/workflows/build.yml` will run this on push.

## Build Environment Used

- Java: OpenJDK 21.0.11 (JRE only — native dist build needs full JDK)
- Gradle: 8.10.2 (via wrapper)
- Kotlin: 2.2.20 (auto-downloaded by Gradle)
- Compose Multiplatform: 1.8.0
- Android SDK: not installed in build environment (CI provides it)
- OS: Debian 13 (Linux x64)
