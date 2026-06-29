package com.wavestream.features.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    source: String,
    url: String,
    onNavigateBack: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    val seekProgress = remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    // Auto-hide controls after 5 seconds of inactivity
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                controlsVisible = !controlsVisible
            },
    ) {
        // Video surface
        PlatformPlayerSurface(
            url = url,
            modifier = Modifier.fillMaxSize(),
            isPlaying = isPlaying,
            onPositionUpdate = { pos ->
                if (!isSeeking) {
                    positionMs = pos
                    if (durationMs > 0) {
                        seekProgress.floatValue = pos.toFloat() / durationMs
                    }
                }
            },
            onDurationUpdate = { durationMs = it },
            onEnded = { onNavigateBack() },
        )

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = source,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }

                // Center play/pause
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }

                // Bottom seekbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // Seekbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = if (isSeeking) seekProgress.floatValue else (if (durationMs > 0) positionMs.toFloat() / durationMs else 0f),
                            onValueChange = { value ->
                                isSeeking = true
                                seekProgress.floatValue = value
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                val targetMs = (seekProgress.floatValue * durationMs).toLong()
                                positionMs = targetMs
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
expect fun PlatformPlayerSurface(
    url: String,
    modifier: Modifier,
    isPlaying: Boolean,
    onPositionUpdate: (Long) -> Unit,
    onDurationUpdate: (Long) -> Unit,
    onEnded: () -> Unit,
)
