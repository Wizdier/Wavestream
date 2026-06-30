package com.wavestream.ui.screens.downloads

import androidx.compose.runtime.Composable
import com.wavestream.ui.components.EmptyState

@Composable
fun DownloadsScreen(onNavigateToPlayer: (String) -> Unit) {
    EmptyState(
        title = "No downloads",
        message = "Download content to watch offline.",
    )
}
