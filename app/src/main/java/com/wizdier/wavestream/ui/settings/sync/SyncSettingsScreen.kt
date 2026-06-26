package com.wizdier.wavestream.ui.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(onBack: () -> Unit) {
    var traktConnected by remember { mutableStateOf(false) }
    var malConnected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_sync)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SyncRow(
                name = stringResource(R.string.sync_trakt),
                connected = traktConnected,
                onToggle = { traktConnected = !traktConnected }
            )
            HorizontalDivider()
            SyncRow(
                name = stringResource(R.string.sync_mal),
                connected = malConnected,
                onToggle = { malConnected = !malConnected }
            )
            HorizontalDivider()
            Text(
                text = "Note: replace trakt_client_id / trakt_client_secret / mal_client_id in strings.xml with your own API credentials before publishing.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun SyncRow(name: String, connected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (connected) "Connected" else "Not connected",
                style = MaterialTheme.typography.bodyMedium,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onToggle) {
            Text(if (connected) stringResource(R.string.sync_logout) else stringResource(R.string.sync_login))
        }
    }
}
