package com.wizdier.wavestream.ui.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.wizdier.wavestream.ui.theme.WaveStreamTheme

/**
 * Fullscreen ExoPlayer host. Implements Nuvio's player UX:
 *  - PiP auto-entry when the user leaves the activity while playing
 *  - Double-tap left/right to skip ±10s (handled in [PlayerScreen])
 *  - External subtitle loading via the ViewModel
 *
 * The player instance is created and released by [PlayerScreen]'s
 * DisposableEffect; this activity only holds a weak reference so PiP can
 * consult `isPlaying` before entering. We do NOT release the player in
 * onDestroy — the Composable already does that, and double-release crashes.
 */
class PlayerActivity : ComponentActivity() {

    @Volatile
    private var player: ExoPlayer? = null
    private var wasPlayingBeforePiP = false

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()

        setContent {
            WaveStreamTheme {
                PlayerScreen(
                    providerId = providerId,
                    url = url,
                    onBack = { finish() },
                    onPlayerReady = { player = it }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = player
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && p != null && p.isPlaying) {
            wasPlayingBeforePiP = true
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && !wasPlayingBeforePiP) {
            player?.pause()
        }
        wasPlayingBeforePiP = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Drop the reference; the Composable's DisposableEffect releases the player.
        player = null
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "providerId"
        const val EXTRA_URL = "url"
    }
}
