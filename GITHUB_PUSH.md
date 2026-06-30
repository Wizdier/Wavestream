# Pushing Wavestream to Your GitHub

This project is ready to push to `https://github.com/wizdier/wavestream`. The git repo is already initialized with the initial commit and the remote is configured.

## Option A — Push via HTTPS with a Personal Access Token

1. **Create the empty repo on GitHub.** Go to <https://github.com/new> and create a new repository:
   - Owner: `wizdier`
   - Name: `wavestream`
   - Visibility: Public (or Private if you prefer)
   - **Do NOT** initialize with README/license/.gitignore (this repo already has all of those)

2. **Create a Personal Access Token.** Go to <https://github.com/settings/tokens?type=beta> and create a fine-grained PAT:
   - Repository access: Only select repositories → `wizdier/wavestream`
   - Permissions → Repository permissions → Contents: Read and write
   - Copy the token (starts with `github_pat_...`)

3. **Push from your local clone:**

   ```bash
   cd wavestream
   git remote set-url origin https://<YOUR_TOKEN>@github.com/wizdier/wavestream.git
   git push -u origin main
   ```

   After the first push, you can reset the URL back to the non-token version:
   ```bash
   git remote set-url origin https://github.com/wizdier/wavestream.git
   ```
   Future pushes will use the cached credentials (or git credential helper).

## Option B — Push via `gh` CLI

If you have the [GitHub CLI](https://cli.github.com/) installed:

```bash
cd wavestream
gh auth login
gh repo create wizdier/wavestream --public --source=. --remote=origin --push
```

## Option C — Push via SSH

If you have an SSH key added to your GitHub account:

```bash
cd wavestream
git remote set-url origin git@github.com:wizdier/wavestream.git
git push -u origin main
```

## Verifying the Push

After pushing, the GitHub Actions CI workflow (`.github/workflows/build.yml`) will trigger automatically. It builds:

- `:library:compileKotlinDesktop` + `:shared:compileKotlinDesktop` (JVM verification)
- `:shared:desktopJar` (desktop uber jar artifact)
- `:app:assembleDebug` (Android APK artifact)

Artifacts are uploaded to the Actions run page. Look for the green checkmark on the latest commit on `main`.

## What's in the Repo

The initial commit contains **187 files** totaling **~22,400 lines** of code:

- `library/` — 145 Kotlin files (CloudStream library, patched)
- `shared/` — 26 Kotlin files (Compose Multiplatform UI)
- `app/` — 1 Kotlin file (Android entry point)
- Gradle config + wrapper
- README, LICENSE (GPL-3.0 inherited from CloudStream), .gitignore
- GitHub Actions CI workflow
- Desktop entry point (`Main.kt`) + native distribution config

## Next Steps After Pushing

1. **Add topics to the repo** — on the GitHub repo page, click the gear icon next to "About" and add: `cloudstream`, `compose-multiplatform`, `kotlin-multiplatform`, `streaming`, `android`, `desktop`, `stremio`.
2. **Enable Issues + Discussions** in repo settings if you want community engagement.
3. **Configure branch protection** for `main` — require CI to pass before merge.
4. **Add release tags** — `git tag v0.1.0 -m "Initial public release" && git push --tags`.
5. **Set up GitHub Pages** if you want to host API docs (run `./gradlew :library:dokkaHtml` first).
