package com.wizdier.wavestream.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The gesture overlay — captures drag and tap gestures on the player surface
 * and translates them to brightness/volume/seek changes with a CloudStream-
 * style visual indicator overlay.
 *
 *  - Left-edge vertical drag → brightness (0..1)
 *  - Right-edge vertical drag → volume (0..maxStreamVolume)
 *  - Horizontal drag → seek (forward/backward)
 *  - Double-tap left third → seek -10s
 *  - Double-tap right third → seek +10s
 *  - Single tap → toggle controller visibility (handled by PlayerView)
 *
 * The overlay is split into three vertical thirds so we can detect which
 * gesture zone the user touched.
 */
@Composable
fun PlayerGestureOverlay(
    player: androidx.media3.exoplayer.ExoPlayer,
    onToggleControls: () -> Unit,
    onSeekHint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }

    var dragHint by remember { mutableStateOf<GestureHint?>(null) }
    var seekAmount by remember { mutableFloatStateOf(0f) }
    var currentBrightness by remember { mutableFloatStateOf(getActivityBrightness(activity)) }
    var currentVolume by remember { mutableFloatStateOf(getCurrentVolume(context)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(player) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val height = size.height
                        val x = offset.x
                        val y = offset.y
                        // Initialize drag state based on starting position
                        dragHint = when {
                            x < width / 3f -> GestureHint.Brightness(currentBrightness)
                            x > 2 * width / 3f -> GestureHint.Volume(currentVolume)
                            else -> GestureHint.Seek(0f)
                        }
                    },
                    onDragEnd = {
                        // Apply seek if accumulated.
                        if (dragHint is GestureHint.Seek) {
                            val amt = (dragHint as GestureHint.Seek).amount
                            if (amt != 0f) {
                                val targetMs = (player.currentPosition + amt.toLong()).coerceAtLeast(0)
                                player.seekTo(targetMs)
                            }
                        }
                        dragHint = null
                        seekAmount = 0f
                    },
                    onDrag = { change, drag ->
                        val width = size.width
                        val height = size.height
                        val x = change.position.x
                        val yDelta = -drag.y  // up = positive
                        val normalized = yDelta / height

                        when (dragHint) {
                            is GestureHint.Brightness -> {
                                val newB = ((dragHint as GestureHint.Brightness).value + normalized).coerceIn(0f, 1f)
                                currentBrightness = newB
                                setActivityBrightness(activity, newB)
                                dragHint = GestureHint.Brightness(newB)
                            }
                            is GestureHint.Volume -> {
                                val maxVol = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val newV = ((dragHint as GestureHint.Volume).value + normalized * maxVol).coerceIn(0f, maxVol.toFloat())
                                currentVolume = newV
                                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(
                                    AudioManager.STREAM_MUSIC, newV.toInt(), 0
                                )
                                dragHint = GestureHint.Volume(newV)
                            }
                            is GestureHint.Seek -> {
                                // 1px ≈ 100ms (so a 200px swipe = ±20s)
                                seekAmount += drag.x * 100f / 1000f  // seconds
                                dragHint = GestureHint.Seek(seekAmount)
                            }
                            else -> {}
                        }
                    }
                )
            }
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val x = offset.x
                        val delta = when {
                            x < width / 3f -> -10_000L
                            x > 2 * width / 3f -> 10_000L
                            else -> return@detectTapGestures
                        }
                        val target = (player.currentPosition + delta).coerceAtLeast(0)
                        player.seekTo(target)
                        onSeekHint(if (delta > 0) "⏩ +10s" else "⏪ -10s")
                    }
                )
            }
    ) {
        // Visual indicator center overlay
        AnimatedVisibility(
            visible = dragHint != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureIndicator(dragHint)
        }
    }
}

@Composable
private fun GestureIndicator(hint: GestureHint?) {
    val displayText: String
    val progress: Float
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    when (hint) {
        is GestureHint.Brightness -> {
            displayText = "${(hint.value * 100).toInt()}%"
            progress = hint.value
            icon = Icons.Outlined.BrightnessHigh
        }
        is GestureHint.Volume -> {
            displayText = "${(hint.value / (getMaxVolume(LocalContext.current)).coerceAtLeast(1f) * 100).toInt()}%"
            progress = if (getMaxVolume(LocalContext.current) > 0) hint.value / getMaxVolume(LocalContext.current) else 0f
            icon = Icons.Outlined.VolumeUp
        }
        is GestureHint.Seek -> {
            val seconds = hint.amount.toInt()
            displayText = "${if (seconds >= 0) "+" else ""}${seconds}s"
            progress = 0f
            icon = if (seconds >= 0) Icons.Outlined.FastForward else Icons.Outlined.FastRewind
        }
        null -> {
            displayText = ""
            progress = 0f
            icon = Icons.Outlined.BrightnessHigh
        }
    }

    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = displayText,
                color = Color.White,
                fontSize = 16.sp
            )
            if (hint is GestureHint.Brightness || hint is GestureHint.Volume) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}

sealed class GestureHint {
    data class Brightness(val value: Float) : GestureHint()
    data class Volume(val value: Float) : GestureHint()
    data class Seek(val amount: Float) : GestureHint()
}

// ── Brightness / volume helpers ──────────────────────────────────────────────

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun getActivityBrightness(activity: Activity?): Float {
    if (activity == null) return 0.5f
    val lp = activity.window.attributes
    return if (lp.screenBrightness >= 0) lp.screenBrightness else 0.5f
}

private fun setActivityBrightness(activity: Activity?, brightness: Float) {
    if (activity == null) return
    val lp = activity.window.attributes
    lp.screenBrightness = brightness
    activity.window.attributes = lp
}

private fun getCurrentVolume(context: Context): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
}

private fun getMaxVolume(context: Context): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
}
