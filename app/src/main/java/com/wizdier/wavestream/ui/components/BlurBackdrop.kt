package com.wizdier.wavestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Nuvio-style blurred backdrop — the hero image is rendered behind a
 * translucent gradient that fades into the surface colour, producing the
 * signature "frosted" header used on Home and Detail screens.
 */
@Composable
fun BlurBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    fadeTo: Color = Color.Transparent
) {
    Box(modifier = modifier) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.4f),
                        1f to fadeTo
                    )
                )
        )
    }
}
