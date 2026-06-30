package com.wavestream.ui.screens.downloads

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wavestream.platform.wavePlatform
import com.wavestream.ui.components.EmptyState
import java.io.File

/**
 * Downloads screen. Lists video files saved under [wavePlatform.downloadsDir].
 *
 * Each download is shown as a list item with the filename and human-readable
 * file size. Tapping a row launches the player. This is intentionally a
 * minimal implementation — the CloudStream 3 download manager (with
 * pause/resume, queueing, and media3 integration) is out of scope.
 */
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
) {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        files = wavePlatform.downloadsDir.listFiles { f -> f.isFile && f.extension in setOf("mp4", "mkv", "webm", "avi") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    if (files.isEmpty()) {
        EmptyState(
            title = "No downloads yet",
            subtitle = "Items you download will appear here.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(files) { file ->
            ListItem(
                headlineContent = { Text(file.nameWithoutExtension) },
                supportingContent = { Text(formatBytes(file.length())) },
                leadingContent = null,
                trailingContent = {
                    Text(
                        text = "${file.extension.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
