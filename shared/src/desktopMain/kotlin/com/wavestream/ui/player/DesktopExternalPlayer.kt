package com.wavestream.ui.player

import java.awt.Desktop
import java.net.URI

/**
 * Desktop-specific [WaveVideoPlayer] that opens the video URL in the user's
 * default system media player / browser. This is a deliberate fallback —
 * bundling a full video decoder (libavformat + libavcodec) in a JVM app
 * would balloon the distribution size by 100+ MB. Production desktop
 * builds should integrate vlcj or javafx-media instead.
 */
class DesktopExternalPlayer : WaveVideoPlayer {
    override fun play(url: String, onReady: (Boolean) -> Unit) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
                onReady(true)
            } else {
                onReady(false)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            onReady(false)
        }
    }

    override fun stop() {
        // No-op — the external player owns playback lifecycle.
    }
}
