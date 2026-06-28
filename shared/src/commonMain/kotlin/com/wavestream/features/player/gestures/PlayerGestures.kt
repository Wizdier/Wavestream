package com.wavestream.features.player.gestures

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/**
 * Player gesture helper — mirrors CloudStream's `PlayerGestureHelper` (1220 lines).
 *
 * Implements:
 *   - Tap zones: tap left third = seek back 10s, tap right third = seek forward 10s, tap center = toggle controls
 *   - Double-tap: left = seek back 10s, right = seek forward 10s
 *   - Horizontal drag (left third): seek
 *   - Vertical drag (left third): brightness
 *   - Vertical drag (right third): volume
 *   - Pinch: zoom (TODO)
 *   - Long press: play at 2x speed (TODO)
 */

enum class GestureZone { LEFT, CENTER, RIGHT }

data class GestureState(
    val isSeeking: Boolean = false,
    val seekDeltaMs: Long = 0L,
    val isAdjustingBrightness: Boolean = false,
    val brightness: Float = 0.5f,
    val isAdjustingVolume: Boolean = false,
    val volume: Float = 0.5f,
    val showControls: Boolean = true,
)

@Composable
fun rememberPlayerGestures(
    onSeek: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
): Modifier {
    var state by remember { mutableStateOf(GestureState()) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragStartX by remember { mutableFloatStateOf(0f) }

    return Modifier.pointerInput(Unit) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()

        detectTapGestures(
            onTap = { offset ->
                val zone = getZone(offset.x, width)
                when (zone) {
                    GestureZone.LEFT -> onSeek(-10_000L)   // seek back 10s
                    GestureZone.RIGHT -> onSeek(10_000L)    // seek forward 10s
                    GestureZone.CENTER -> onToggleControls()
                }
            },
            onDoubleTap = { offset ->
                val zone = getZone(offset.x, width)
                when (zone) {
                    GestureZone.LEFT -> onSeek(-10_000L)
                    GestureZone.RIGHT -> onSeek(10_000L)
                    GestureZone.CENTER -> {}
                }
            },
        )
    }.pointerInput(Unit) {
        val width = size.width.toFloat()
        detectDragGestures(
            onDragStart = { offset ->
                dragStartX = offset.x
                dragStartY = offset.y
            },
            onDrag = { change, dragAmount ->
                val zone = getZone(dragStartX, size.width.toFloat())
                val dx = dragAmount.x
                val dy = dragAmount.y

                when {
                    // Horizontal drag on left/right zone = seek
                    kotlin.math.abs(dx) > kotlin.math.abs(dy) && zone != GestureZone.CENTER -> {
                        state = state.copy(isSeeking = true, seekDeltaMs = (dx * 100).toLong())
                    }
                    // Vertical drag on left zone = brightness
                    zone == GestureZone.LEFT -> {
                        val newBrightness = (state.brightness - dy / 500f).coerceIn(0f, 1f)
                        state = state.copy(isAdjustingBrightness = true, brightness = newBrightness)
                        onBrightnessChange(newBrightness)
                    }
                    // Vertical drag on right zone = volume
                    zone == GestureZone.RIGHT -> {
                        val newVolume = (state.volume - dy / 500f).coerceIn(0f, 1f)
                        state = state.copy(isAdjustingVolume = true, volume = newVolume)
                        onVolumeChange(newVolume)
                    }
                }
                change.consume()
            },
            onDragEnd = {
                if (state.isSeeking) {
                    onSeek(state.seekDeltaMs)
                }
                state = state.copy(isSeeking = false, isAdjustingBrightness = false, isAdjustingVolume = false)
            },
        )
    }
}

private fun getZone(x: Float, width: Float): GestureZone {
    val third = width / 3f
    return when {
        x < third -> GestureZone.LEFT
        x > 2 * third -> GestureZone.RIGHT
        else -> GestureZone.CENTER
    }
}
