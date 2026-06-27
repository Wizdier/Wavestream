package com.wizdier.wavestream.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.ui.theme.ShimmerBase
import com.wizdier.wavestream.ui.theme.ShimmerHighlight

/**
 * Shimmer placeholder effect for loading states. Sweeps a highlight gradient
 * across a colored base — gives the user a sense that content is on its way
 * without the harsh flash of a spinner.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 100f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(brush)
    )
}

/** A poster-shaped shimmer (2:3 aspect ratio). */
@Composable
fun ShimmerPoster(modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier
            .width(110.dp)
            .aspectRatio(2f / 3f),
        cornerRadius = 12
    )
}

/** A wide card shimmer (16:9 aspect ratio) — for Continue Watching. */
@Composable
fun ShimmerWideCard(modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier
            .width(200.dp)
            .aspectRatio(16f / 9f),
        cornerRadius = 12
    )
}

/** A shimmer row that mimics a Home row — title + horizontal posters. */
@Composable
fun ShimmerRow() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(120.dp)
                .height(20.dp),
            cornerRadius = 4
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(5) { ShimmerPoster() }
        }
    }
}

/** A full Home screen shimmer — multiple rows. */
@Composable
fun ShimmerHome() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Continue Watching row
        ShimmerRow()
        // Trending row
        ShimmerRow()
        // Popular row
        ShimmerRow()
    }
}

/** A search results grid shimmer. */
@Composable
fun ShimmerSearchGrid() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerPoster(modifier = Modifier.weight(1f))
                ShimmerPoster(modifier = Modifier.weight(1f))
                ShimmerPoster(modifier = Modifier.weight(1f))
            }
        }
    }
}
