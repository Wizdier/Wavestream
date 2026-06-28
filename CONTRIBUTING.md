# Contributing to Wavestream

Thanks for your interest in contributing! This guide covers the basics.

## Development Setup

1. **Prerequisites**:
   - JDK 17+
   - Android SDK (for Android builds)
   - Gradle 9.4.1+ (wrapper included)

2. **Clone + build**:
   ```bash
   git clone https://github.com/wavestream/wavestream.git
   cd wavestream
   ./gradlew :shared:compileKotlinDesktop
   ```

3. **Run on desktop** (for development):
   ```bash
   ./run.sh
   ```

4. **Build Android APK**:
   ```bash
   export ANDROID_HOME=/path/to/android/sdk
   echo "sdk.dir=$ANDROID_HOME" > local.properties
   ./gradlew :app:assembleStableDebug
   ```

## Project Structure

- `shared/` — KMP module with all shared code (commonMain + androidMain + desktopMain)
- `app/` — Android application module (thin wrapper around shared)
- `examples/` — Example plugins + repository JSON

## Writing Extensions

See `examples/ExampleProvider.kt` for a CloudStream-style provider example.
See `examples/example-js-scraper.js` for a JS plugin scraper example.

### CloudStream-style provider

1. Create a Kotlin class extending `MainAPI`
2. Override `search`, `load`, `loadLinks`
3. Create a `BasePlugin` subclass that registers your provider
4. Annotate with `@WavestreamPlugin`
5. Compile against `com.wavestream:shared`
6. Package as `.ws3` (zip with manifest.json + classes.dex)

### Stremio addon

Just add the manifest URL in the Extensions screen. No code needed.

### JS plugin scraper

1. Create a `.js` file exporting `getStreams(tmdbId, mediaType, season, episode)`
2. Return an array of stream objects: `{ url: "...", title: "...", quality: "1080p" }`
3. Optionally export `onSettings()` for a settings UI

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4-space indentation
- Add KDoc to all public classes/functions
- Keep functions short (< 50 lines where possible)

## Pull Requests

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m 'Add my-feature'`
4. Push: `git push origin feature/my-feature`
5. Open a pull request

## Reporting Issues

Use the GitHub Issues tab. Include:
- Device + Android version
- App version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output (if applicable)

## License

By contributing, you agree that your contributions will be licensed under the MIT license.
