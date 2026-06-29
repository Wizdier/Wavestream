package com.wavestream.ui.screens.downloads

import androidx.compose.runtime.Composable
import com.wavestream.ui.components.EmptyState

@Composable
fun DownloadsScreen(
    onNavigateToPlayer: (url: String) -> Unit,
) {
    EmptyState(
        title = "No downloads yet",
        message = "Download shows and movies to watch offline.",
    )
}
