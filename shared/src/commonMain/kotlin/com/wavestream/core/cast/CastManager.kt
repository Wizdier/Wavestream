package com.wavestream.core.cast

import com.wavestream.api.ExtractorLink
import com.wavestream.api.SubtitleFile

data class CastSession(
    val id: String, val name: String,
    val isPlaying: Boolean = false, val positionMs: Long = 0L,
    val durationMs: Long = 0L, val volume: Float = 1f,
)

expect class CastManager() {
    fun isAvailable(): Boolean
    fun getCurrentSession(): CastSession?
    fun startSession(appId: String, callback: (Boolean) -> Unit)
    fun endSession()
    fun loadMedia(link: ExtractorLink, subtitles: List<SubtitleFile>, positionMs: Long = 0)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
}

expect class FcastManager() {
    fun isAvailable(): Boolean
    fun discoverReceivers(): List<String>
    fun connect(host: String): Boolean
    fun disconnect()
    fun cast(url: String, title: String, positionMs: Long = 0)
}
