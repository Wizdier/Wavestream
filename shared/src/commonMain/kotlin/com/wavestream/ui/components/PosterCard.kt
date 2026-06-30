package com.wavestream.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * Poster card used throughout the home, search, and library screens.
 *
 * Features:
 * - 2:3 aspect ratio (standard movie poster)
 * - Shimmer placeholder while the image loads
 * - Subtle press-scale animation (0.96f) for tactile feedback
 * - Optional quality badge in the top-left corner
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    quality: String? = null,
    onClick: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val scale = if (pressed) 0.96f else 1f

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                pressed = true
                onClick()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            // Track image state so we can show a shimmer overlay until the
            // image is successfully loaded.
            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

            // Show shimmer while loading, on error, or before request starts
            when (imageState) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                is AsyncImagePainter.State.Empty -> ShimmerBox(modifier = Modifier.fillMaxSize())
                else -> Unit
            }

            // Render the actual image on top — once it's loaded, the shimmer
            // below is covered.
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { imageState = it },
            )

            if (quality != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopStart),
                ) {
                    Text(
                        text = quality,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Shimmer placeholder used by [PosterCard] while images load.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    val brush: Brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor,
        ),
        start = androidx.compose.ui.geometry.Offset(
            x = -300f + translate * 600f,
            y = 0f,
        ),
        end = androidx.compose.ui.geometry.Offset(
            x = translate * 600f,
            y = 100f,
        ),
    )

    Box(
        modifier = modifier
            .background(brush),
    )
}
