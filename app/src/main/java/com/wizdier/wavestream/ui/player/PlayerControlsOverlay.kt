package com.wizdier.wavestream.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Immersive player controls overlay — CloudStream-inspired but with:
 *  - Auto-hide after 4 seconds of inactivity
 *  - Center play/pause + skip ±10s buttons
 *  - Bottom: title, seek bar with timestamps, speed/subtitle/settings buttons
 *  - Top: back button, lock button
 *  - Gradient scrims top and bottom for legibility
 *  - Tap anywhere to toggle controls visibility
 */
@Composable
fun PlayerControlsOverlay(
    player: ExoPlayer,
    title: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track playback state for UI updates
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }

    // Poll player state every 500ms while controls are visible
    LaunchedEffect(player, isVisible) {
        while (isVisible) {
            isPlaying = player.isPlaying
            currentPosition = player.currentPosition
            duration = player.duration
            delay(500)
        }
    }

    // Auto-hide after 4 seconds of inactivity (only while playing)
    LaunchedEffect(isVisible, isPlaying) {
        if (isVisible && isPlaying) {
            delay(4000)
            onToggleVisibility()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onToggleVisibility)) {
            // ── Top gradient scrim ──────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.7f),
                            1f to Color.Transparent
                        )
                    )
            )

            // ── Top bar: back + title + lock ───────────────────────
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* TODO: lock controls */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock",
                        tint = Color.White
                    )
                }
            }

            // ── Center controls: skip back + play/pause + skip forward ──
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { player.seekBack() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Replay10,
                        contentDescription = "Back 10s",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = { player.seekForward() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forward10,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // ── Bottom gradient scrim ──────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )

            // ── Bottom controls: seek bar + timestamps + actions ───
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Seek bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { fraction ->
                            val newPos = (fraction * duration).toLong()
                            player.seekTo(newPos)
                            currentPosition = newPos
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTime(duration),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed picker
                    var speedMenuOpen by remember { mutableStateOf(false) }
                    var currentSpeed by remember { mutableStateOf(1f) }
                    Box {
                        IconButton(onClick = { speedMenuOpen = true }) {
                            Icon(Icons.Outlined.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x" + if (speed == currentSpeed) " ✓" else "") },
                                    onClick = {
                                        currentSpeed = speed
                                        player.setPlaybackSpeed(speed)
                                        speedMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Subtitle picker
                    IconButton(onClick = { /* TODO: subtitle picker */ }) {
                        Icon(Icons.Outlined.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                    }

                    // Quality / settings picker
                    IconButton(onClick = { /* TODO: quality picker */ }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Settings", tint = Color.White)
                    }

                    // Sleep timer
                    var sleepTimerOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { sleepTimerOpen = true }) {
                        Icon(Icons.Outlined.Bedtime, contentDescription = "Sleep timer", tint = Color.White)
                    }
                    if (sleepTimerOpen) {
                        SleepTimerDialog(
                            onDismiss = { sleepTimerOpen = false },
                            onSet = { minutes ->
                                // TODO: start actual timer — for now just dismiss
                                sleepTimerOpen = false
                            },
                            onCancel = { /* TODO: cancel timer */ }
                        )
                    }

                    // Next episode
                    IconButton(onClick = { /* TODO: next episode */ }) {
                        Icon(Icons.Outlined.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
