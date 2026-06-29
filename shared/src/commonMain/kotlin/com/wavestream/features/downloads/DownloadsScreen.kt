package com.wavestream.features.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavestream.api.TvType
import com.wavestream.ui.components.EmptyState

/**
 * Downloads screen — mirrors CloudStream's DownloadFragment.
 * This is a main tab (accessible via bottom navigation).
 */
@Composable
fun DownloadsScreen(
    onNavigateToPlayer: (url: String) -> Unit,
) {
    // Load from VideoDownloadManager
    val downloadState by VideoDownloadManager.downloads.collectAsState()
    val downloads = downloadState.values
        .filter { it.status is DownloadStatus.Completed }
        .map { task ->
            DownloadedItem(
                id = task.id,
                title = task.title,
                subtitle = task.subtitle,
                filePath = task.outputFile.absolutePath,
                sizeText = formatBytes(task.outputFile.length()),
                type = task.type,
            )
        }

    if (downloads.isEmpty()) {
        EmptyState(
            title = "No downloads yet",
            message = "Download episodes to watch offline.",
            icon = "📥",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(downloads, key = { it.id }) { item ->
                DownloadRow(
                    item = item,
                    onClick = { onNavigateToPlayer(item.filePath) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadedItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp, 68.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

@kotlinx.serialization.Serializable
data class DownloadedItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val filePath: String,
    val sizeText: String,
    val type: TvType,
)
