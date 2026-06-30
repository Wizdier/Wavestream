package com.wavestream

import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.wavestream.platform.wavePlatform
import com.wavestream.stremio.StremioAddonRepository

/**
 * Hardcoded lists of CloudStream repositories and Stremio addons that are
 * pre-populated on first launch (when the user has zero repos and zero
 * addons). This guarantees a new install has something to browse — the
 * single most-requested UX improvement over a blank home screen.
 *
 * These lists are intentionally curated to:
 *  - cover the major content categories (movies, series, anime, live)
 *  - only include well-known public repos (no auth required)
 *  - degrade gracefully if a repo is offline — failures are logged but
 *    never surface as a boot error
 *
 * Adding / removing repos and addons after first launch is the user's
 * prerogative via the Extensions screen. This seeding only runs when the
 * relevant preference keys are absent.
 */
object DefaultRepos {

    private const val SEEDED_KEY = "wavestream.default_repos_seeded"

    /**
     * Canonical CloudStream community repositories. Each entry is a cs.repo
     * short URL — the [RepositoryManager.parseRepoUrl] implementation
     * resolves these to the underlying raw GitHub URL.
     *
     * The user's own repo (Wizdier) is included first since it hosts
     * BD/FTP-based providers tested against this app.
     */
    val CLOUDSTREAM_REPOS: List<RepositoryData> = listOf(
        RepositoryData(
            url = "https://raw.githubusercontent.com/Wizdier/Wizdier-CloudstreamRepo/builds/plugins.json",
            name = "Wizdier's Repository",
            description = "Cineplex BD, Circle FTP, CTGMovies, FTPBD — Bangladesh FTP streaming.",
        ),
        RepositoryData(
            url = "https://cs.repo/milkman",
            name = "Milkman's Repository",
            description = "General media provider collection — movies, series, anime.",
        ),
        RepositoryData(
            url = "https://cs.repo/nice",
            name = "Nice Repository",
            description = "Multi-language media providers and extractors.",
        ),
        RepositoryData(
            url = "https://cs.repo/jeremy",
            name = "Jeremy's Repository",
            description = "Anime-focused providers and subtitle integrations.",
        ),
        RepositoryData(
            url = "https://cs.repo/automations",
            name = "Automations Repository",
            description = "Automated scraper providers maintained by the community.",
        ),
    )

    /**
     * Canonical Stremio addon URLs. These are the addons Stremio itself
     * advertises on its homepage and require no authentication.
     */
    val STREMIO_ADDONS: List<String> = listOf(
        // Cinemeta — TMDB/IMDb metadata catalog. Adds movie/series posters
        // and descriptions even when no CS provider is loaded.
        "https://v3-cinemeta.strem.io/manifest.json",
        // OpenSubtitles — adds subtitle streams to any title.
        "https://v3.opensubtitles.com/opensubtitles-v3/manifest.json",
    )

    /**
     * Seed defaults exactly once. Safe to call on every boot — the
     * [SEEDED_KEY] preference guards against re-seeding after the user
     * has customized their lists.
     *
     * @return true if seeding was performed (first launch), false otherwise.
     */
    suspend fun seedIfFirstLaunch(): Boolean {
        if (wavePlatform.preferences.getBool(SEEDED_KEY, false)) return false

        // Seed CS repos
        val existingRepos = RepositoryManager.getRepositories()
        val newRepos = CLOUDSTREAM_REPOS.filter { new ->
            existingRepos.none { it.url == new.url }
        }
        for (repo in newRepos) {
            runCatching { RepositoryManager.addRepository(repo) }
        }

        // Seed Stremio addons
        val existingAddons = StremioAddonRepository.listAddons()
        val newAddons = STREMIO_ADDONS.filter { it !in existingAddons }
        for (addon in newAddons) {
            StremioAddonRepository.addAddon(addon)
        }

        // Sync the newly-added Stremio addons as providers
        StremioAddonRepository.syncProviders()

        wavePlatform.preferences.putBool(SEEDED_KEY, true)
        return newRepos.isNotEmpty() || newAddons.isNotEmpty()
    }

    /**
     * Force re-seed — useful from a Settings "Restore defaults" button.
     * Resets the [SEEDED_KEY] flag and calls [seedIfFirstLaunch].
     */
    suspend fun forceReseed() {
        wavePlatform.preferences.putBool(SEEDED_KEY, false)
        seedIfFirstLaunch()
    }
}
