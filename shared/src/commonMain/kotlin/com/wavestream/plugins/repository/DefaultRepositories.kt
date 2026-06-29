package com.wavestream.plugins.repository

/**
 * Default CloudStream repositories — pre-loaded so users see content immediately on first launch.
 *
 * These are the most popular community repositories from the CloudStream ecosystem.
 * Users can add/remove them from the Extensions screen.
 *
 * Source: https://github.com/recloudstream/cs-repos
 */
object DefaultRepositories {
    val ALL: List<RepositoryData> = listOf(
        RepositoryData(
            url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            name = "CloudStream Official Extensions",
        ),
        RepositoryData(
            url = "https://raw.githubusercontent.com/michaldiast1/cs3repo/main/repo.json",
            name = "MichalDiar Repo",
        ),
    )

    /**
     * Popular Stremio addons — for first-time users who want to test Stremio functionality.
     * Cinemeta is the official Stremio metadata addon (no streams, but provides catalogs + metadata).
     *
     * Find more addons at https://stremio-addons.netlify.app/
     */
    val DEFAULT_STREMIO_ADDONS: List<String> = listOf(
        // Official Stremio catalog addon — provides movie/series metadata
        "https://v3-cinemeta.strem.io/manifest.json",
    )
}
