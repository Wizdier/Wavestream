package com.wizdier.wavestream.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R
import com.wizdier.wavestream.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onOpenPlayer: (providerId: String, url: String) -> Unit,
    viewModel: DownloadsViewModel = koinViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.downloads_title)) }) }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyState(message = stringResource(R.string.downloads_empty), modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(downloads, key = { it.rowId }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        item.qualityLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${(item.progress * 100).toInt()}% · ${item.status}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    when (item.status) {
                        "running" -> TextButton(onClick = { viewModel.pause(item.rowId) }) {
                            Text(stringResource(R.string.downloads_pause))
                        }
                        "paused", "queued" -> TextButton(onClick = { viewModel.resume(item.rowId) }) {
                            Text(stringResource(R.string.downloads_resume))
                        }
                        "completed" -> TextButton(onClick = { onOpenPlayer(item.providerId, item.url) }) {
                            Text(stringResource(R.string.detail_play))
                        }
                    }
                    TextButton(onClick = { viewModel.cancel(item.rowId) }) {
                        Text(stringResource(R.string.downloads_delete))
                    }
                }
            }
        }
    }
}
