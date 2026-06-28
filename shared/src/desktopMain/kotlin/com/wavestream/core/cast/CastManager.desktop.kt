package com.wavestream.core.cast

import com.wavestream.api.ExtractorLink
import com.wavestream.api.SubtitleFile

actual class CastManager actual constructor() {
    actual fun isAvailable(): Boolean = false
    actual fun getCurrentSession(): CastSession? = null
    actual fun startSession(appId: String, callback: (Boolean) -> Unit) { callback(false) }
    actual fun endSession() {}
    actual fun loadMedia(link: ExtractorLink, subtitles: List<SubtitleFile>, positionMs: Long) {}
    actual fun play() {}
    actual fun pause() {}
    actual fun seekTo(positionMs: Long) {}
    actual fun setVolume(volume: Float) {}
}

actual class FcastManager actual constructor() {
    actual fun isAvailable(): Boolean = false
    actual fun discoverReceivers(): List<String> = emptyList()
    actual fun connect(host: String): Boolean = false
    actual fun disconnect() {}
    actual fun cast(url: String, title: String, positionMs: Long) {}
}
