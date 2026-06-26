# Contributing to WaveStream

Thanks for your interest in improving WaveStream! This guide covers the
basics. For project context, read the [README](README.md) first.

## Code of conduct

Be kind. WaveStream is built on top of two upstream communities
(CloudStream and Nuvio) and we welcome contributions that respect both.
Personal attacks, harassment, or discrimination will not be tolerated.

## Before you start

- WaveStream is GPL-3.0. By contributing you agree your code will be
  released under the same license.
- If you are porting code from CloudStream or Nuvio, **credit the upstream
  source** in your commit message and in the source file's KDoc.
- Do not submit code that scrapes or streams copyrighted content without
  permission — WaveStream is a client, not a content source.

## Getting set up

```bash
git clone https://github.com/wizdier/wavestream.git
cd wavestream
./gradlew :app:assembleDebug
```

You need:
- JDK 17
- Android SDK 34
- Android Studio Koala or newer (recommended)

## Branching

- Fork the repo and branch from `main`.
- Use a descriptive branch name: `feature/continue-watching-carousel`,
  `fix/player-pip-crash`, `docs/repo-format`.
- Keep branches focused — one feature / fix per branch.

## Commit messages

Follow the conventional-commits style:

```
feat(player): add brightness swipe gesture on left edge
fix(search): debounce filter chips to avoid duplicate queries
docs(readme): document repo JSON manifest format
chore(deps): bump media3 to 1.4.1
```

## Code style

- Kotlin, 4-space indent, no tabs.
- Public API surface should carry KDoc — at minimum a one-sentence summary
  plus a `@see` reference when porting from upstream.
- Prefer coroutines + Flow over callbacks. Prefer Compose over XML.
- Run `./gradlew :app:lint` before pushing.

## Tests

- Add unit tests for new repository / sync logic where reasonable.
- UI tests are not required for early-stage contributions but welcome.

## Pull request flow

1. Open a PR against `main`.
2. In the PR description, describe:
   - **What** changed
   - **Why** it changed (the user-facing problem)
   - **How** it was tested
   - Any **breaking** changes or migration steps
3. CI runs `./gradlew :app:assembleDebug` on your PR — make sure it's green.
4. Address review feedback by pushing new commits (don't squash mid-review).

## Adding a new provider

The built-in `PublicDomainProvider` is the reference implementation. To
contribute a new built-in provider:

1. Implement `com.wizdier.wavestream.data.api.Provider`.
2. Register it in `PluginLoader.builtin`.
3. Add a smoke-test entry that calls `search()` and `load()` against a
   known-stable URL.
4. Document the provider's content scope in your PR description.

For providers that should ship as installable extensions instead of
built-in, publish them as a separate APK with the
`com.wizdier.wavestream.provider.class` meta-data key in their manifest,
and add their repo JSON URL to your settings.

## Reporting issues

When filing an issue, include:
- WaveStream version (Settings → About → Version)
- Android version and device
- Installed provider repos
- Steps to reproduce
- Expected vs. actual behaviour
- Logs if you have them (logcat with the `WaveStream` tag)

## Licensing

By contributing, you agree your contributions will be licensed under the
GPL-3.0 license that covers this project.
