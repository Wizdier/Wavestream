package com.wizdier.wavestream.data.api

import kotlinx.serialization.Serializable

/**
 * Catalog types supported by WaveStream providers. Mirrors CloudStream's TvType
 * enum but adds Anime as a first-class category (a Nuvio influence — Nuvio
 * unifies anime with movies/series in the same browsing surface).
 */
enum class CatalogType(val slug: String, val displayName: String) {
    MOVIES("movies", "Movies"),
    SERIES("series", "Series"),
    ANIME("anime", "Anime"),
    DOCUMENTARIES("documentaries", "Documentaries"),
    LIVE("live", "Live TV"),
    OTHER("other", "Other")
}

/**
 * Quality buckets used for the per-source quality picker (a CloudStream core
 * feature). Providers can attach one or more [Quality] tags to each video.
 */
enum class Quality(val label: String) {
    P360("360p"),
    P480("480p"),
    P720("720p"),
    P1080("1080p"),
    P1440("1440p"),
    P2160("4K"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(raw: String?): Quality {
            if (raw == null) return UNKNOWN
            val lower = raw.lowercase().replace("p", "").trim()
            return when (lower) {
                "360" -> P360
                "480" -> P480
                "720" -> P720
                "1080", "full hd", "fhd" -> P1080
                "1440", "2k" -> P1440
                "2160", "4k", "uhd" -> P2160
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Represents a single video stream returned by a provider. CloudStream's
 * [ExtractorLink] equivalent — but modelled in pure Kotlin for easy reuse
 * across providers and the WaveStream download manager.
 */
@Serializable
data class VideoLink(
    val name: String,
    val url: String,
    val quality: Quality = Quality.UNKNOWN,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null,
    val extractorType: ExtractorType = ExtractorType.DIRECT,
    val isM3u8: Boolean = url.contains(".m3u8", ignoreCase = true),
)

enum class ExtractorType { DIRECT, EXTRACTOR, TORRENT, DASH }

/**
 * External subtitle file shipped alongside a video. CloudStream-compatible.
 */
@Serializable
data class SubtitleFile(
    val lang: String,
    val url: String,
    val format: SubtitleFormat = SubtitleFormat.VTT
)

enum class SubtitleFormat(val mime: String) {
    VTT("text/vtt"),
    SRT("application/x-subrip"),
    ASS("text/x-ssa")
}
