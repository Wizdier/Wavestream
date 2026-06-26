package com.wizdier.wavestream.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDetail: (providerId: String, url: String) -> Unit,
    viewModel: HistoryViewModel = koinViewModel()
) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clear() }) {
                            Text(stringResource(R.string.history_clear))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyState(message = stringResource(R.string.history_empty), modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(history, key = { it.rowId }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(entry.providerId, entry.url) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!entry.posterUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = entry.posterUrl,
                            contentDescription = entry.title,
                            modifier = Modifier
                                .width(64.dp)
                                .height(90.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Spacer(Modifier.width(64.dp).height(90.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "S${entry.season} · E${entry.episode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val pct = if (entry.durationMs > 0) (entry.progressMs * 100 / entry.durationMs).toInt() else 0
                        Text(
                            text = "$pct% watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.remove(entry.rowId) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}
