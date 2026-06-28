package com.wavestream.api

import kotlinx.serialization.Serializable

/**
 * Type of media. Mirrors CloudStream's TvType enum.
 * Used to categorize providers and filter search results.
 */
@Serializable
enum class TvType {
    Movie,
    AnimeMovie,
    TvSeries,
    Cartoon,
    Anime,
    OVA,
    Torrent,
    Documentary,
    AsianDrama,
    Live,
    NSFW,
    Others,
    Music,
    AudioBook,
    CustomMedia,
    Audio,
    Podcast,
    Video;

    fun isMovieType(): Boolean = when (this) {
        AnimeMovie, Live, Movie, Torrent, Video -> true
        else -> false
    }

    fun isEpisodeBased(): Boolean = when (this) {
        Anime, AsianDrama, Cartoon, TvSeries -> true
        else -> false
    }

    fun isAudioType(): Boolean = when (this) {
        Audio, AudioBook, Music, Podcast -> true
        else -> false
    }

    fun isLiveStream(): Boolean = this == Live

    fun isAnimeOp(): Boolean = this == Anime || this == OVA

    /** Folder prefix for downloads (mirrors CloudStream). */
    fun folderPrefix(): String = when (this) {
        Anime -> "Anime"
        AnimeMovie -> "Movies"
        AsianDrama -> "AsianDramas"
        Audio -> "Audio"
        AudioBook -> "AudioBooks"
        Cartoon -> "Cartoons"
        CustomMedia -> "Media"
        Documentary -> "Documentaries"
        Live -> "LiveStreams"
        Movie -> "Movies"
        Music -> "Music"
        NSFW -> "NSFW"
        OVA -> "OVAs"
        Others -> "Others"
        Podcast -> "Podcasts"
        Torrent -> "Torrents"
        TvSeries -> "TVSeries"
        Video -> "Videos"
    }
}

@Serializable
enum class DubStatus(val id: Int) {
    None(-1),
    Dubbed(1),
    Subbed(0);
}

@Serializable
enum class ShowStatus { Completed, Ongoing }

@Serializable
enum class SearchQuality {
    Cam, CamRip, HdCam, Telesync, WorkPrint, Telecine,
    HQ, HD, HDR, BlueRay, DVD, SD, FourK, UHD, SDR, WebRip
}

fun getQualityFromString(string: String?): SearchQuality? {
    val check = string?.trim()?.lowercase()?.replace(" ", "") ?: return null
    return when (check) {
        "cam" -> SearchQuality.Cam
        "camrip" -> SearchQuality.CamRip
        "hdcam", "hdtc", "hdts" -> SearchQuality.HdCam
        "hq", "highquality" -> SearchQuality.HQ
        "hd", "hdrip", "hdtv", "highdefinition", "fhd" -> SearchQuality.HD
        "bluray", "blueray", "blu", "br", "blue" -> SearchQuality.BlueRay
        "dvd", "dvdrip", "dvdscr" -> SearchQuality.DVD
        "sd", "standard" -> SearchQuality.SD
        "4k" -> SearchQuality.FourK
        "uhd" -> SearchQuality.UHD
        "webrip", "webdl", "web" -> SearchQuality.WebRip
        "hdr" -> SearchQuality.HDR
        "sdr" -> SearchQuality.SDR
        "ts" -> SearchQuality.Telesync
        "tc" -> SearchQuality.Telecine
        "wp", "workprint" -> SearchQuality.WorkPrint
        else -> null
    }
}

/** Quality constants — used in ExtractorLink.quality. */
@Serializable
enum class Qualities(val value: Int, val defaultPriority: Int) {
    Unknown(400, 4),
    P144(144, 0),
    P240(240, 2),
    P360(360, 3),
    P480(480, 4),
    P720(720, 5),
    P1080(1080, 6),
    P1440(1440, 7),
    P2160(2160, 8);

    companion object {
        fun getStringByInt(qual: Int?): String = when (qual) {
            0 -> "Auto"
            Unknown.value -> ""
            P2160.value -> "4K"
            null -> ""
            else -> "${qual}p"
        }

        fun getStringByIntFull(quality: Int): String = when (quality) {
            0 -> "Auto"
            Unknown.value -> "Unknown"
            P2160.value -> "4K"
            else -> "${quality}p"
        }
    }
}

fun getQualityFromName(qualityName: String?): Int {
    val match = qualityName?.lowercase()?.replace("p", "")?.trim() ?: return Qualities.Unknown.value
    return when (match) {
        "4k" -> Qualities.P2160.value
        else -> match.toIntOrNull() ?: Qualities.Unknown.value
    }
}

/** Type of media an ExtractorLink points to. */
@Serializable
enum class ExtractorLinkType {
    VIDEO,
    M3U8,
    DASH;

    companion object {
        fun inferFromUrl(url: String): ExtractorLinkType = when {
            url.contains(".m3u8", ignoreCase = true) -> M3U8
            url.contains(".mpd", ignoreCase = true) -> DASH
            else -> VIDEO
        }
    }
}

enum class ProviderType { MetaProvider, DirectProvider }
enum class VPNStatus { None, MightBeNeeded, Torrent }
