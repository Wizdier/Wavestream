package com.wavestream.plugins.stremio

import kotlinx.serialization.Serializable

/**
 * Stremio addon manifest — mirrors NuvioMobile's `AddonManifest`.
 *
 * A Stremio addon is an HTTP service that exposes a JSON manifest at `/manifest.json`.
 * The manifest declares what resource endpoints the addon supports (catalog, meta, stream),
 * what types it handles (movie, series, etc.), and any behavior hints.
 */
@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String,
    val logoUrl: String? = null,
    val resources: List<AddonResource>,
    val types: List<String>,
    val idPrefixes: List<String> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
    val behaviorHints: AddonBehaviorHint = AddonBehaviorHint(),
) {
    var transportUrl: String = ""
}

@Serializable
data class AddonResource(
    val name: String,            // "catalog", "meta", "stream", "subtitles"
    val types: List<String>,
    val idPrefixes: List<String> = emptyList(),
)

@Serializable
data class AddonCatalog(
    val type: String,            // "movie", "series"
    val id: String,              // "top", "trending", custom
    val name: String,
    val extra: List<AddonExtraProperty> = emptyList(),
)

@Serializable
data class AddonExtraProperty(
    val name: String,            // "genre", "search", "skip"
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int? = null,
)

@Serializable
data class AddonBehaviorHint(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val adult: Boolean = false,
    val p2p: Boolean = false,
)

/**
 * Managed addon — a manifest + its installation state.
 * Mirrors NuvioMobile's `ManagedAddon`.
 */
data class ManagedAddon(
    val manifestUrl: String,
    val manifest: AddonManifest? = null,
    val userSetName: String? = null,
    val enabled: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = enabled && manifest != null

    val displayTitle: String
        get() = userSetName?.takeIf { it.isNotBlank() && it != manifest?.name }
            ?: manifest?.name
            ?: manifestUrl.substringBefore("?").substringAfterLast("/").ifBlank { "Addon" }
}

data class AddonsUiState(
    val addons: List<ManagedAddon> = emptyList(),
)

fun List<ManagedAddon>.enabledAddons(): List<ManagedAddon> = filter { it.enabled }

// ============================================================================
// Catalog + Meta + Stream response shapes
// ============================================================================

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val posterShape: String = "poster",
)

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList(),
)

@Serializable
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val country: String? = null,
    val language: String? = null,
    val videos: List<StremioVideo> = emptyList(),
    val links: List<StremioLink> = emptyList(),
    val behaviorHints: StremioBehaviorHints? = null,
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMeta,
)

@Serializable
data class StremioVideo(
    val id: String,
    val title: String,
    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val runtime: Int? = null,
)

@Serializable
data class StremioLink(
    val name: String,
    val category: String,
    val url: String,
)

@Serializable
data class StremioBehaviorHints(
    val bingeGroup: String? = null,
    val notWebReady: Boolean = false,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
    val proxyHeaders: StremioProxyHeaders? = null,
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null,
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val externalUrl: String? = null,
    val sources: List<String> = emptyList(),
    val behaviorHints: StremioBehaviorHints? = null,
    val subtitles: List<StremioSubtitle> = emptyList(),
)

@Serializable
data class StremioStreamsResponse(
    val streams: List<StremioStream> = emptyList(),
)

@Serializable
data class StremioSubtitle(
    val url: String,
    val lang: String? = null,
    val language: String? = null,
    val label: String? = null,
)
