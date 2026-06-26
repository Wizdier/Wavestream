package com.wizdier.wavestream.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenRepos: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var swipeGestures by remember { mutableStateOf(true) }
    var autoPip by remember { mutableStateOf(true) }
    var skipIntro by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // General
            SectionHeader(stringResource(R.string.settings_general))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_providers)) },
                supportingContent = { Text(stringResource(R.string.repos_title)) },
                modifier = Modifier.clickable(onClick = onOpenRepos)
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Favorite, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.favorites_title)) },
                modifier = Modifier.clickable(onClick = onOpenFavorites)
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.history_title)) },
                modifier = Modifier.clickable(onClick = onOpenHistory)
            )

            // Appearance
            SectionHeader(stringResource(R.string.settings_appearance))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Brightness6, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_dark_mode)) }
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                trailingContent = {
                    Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
                }
            )

            // Player
            SectionHeader(stringResource(R.string.settings_player))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_player_swipe)) },
                trailingContent = { Switch(checked = swipeGestures, onCheckedChange = { swipeGestures = it }) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_player_pip)) },
                trailingContent = { Switch(checked = autoPip, onCheckedChange = { autoPip = it }) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_player_skip_intro)) },
                trailingContent = { Switch(checked = skipIntro, onCheckedChange = { skipIntro = it }) }
            )

            // Sync
            SectionHeader(stringResource(R.string.settings_sync))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.sync_trakt) + " / " + stringResource(R.string.sync_mal)) },
                modifier = Modifier.clickable(onClick = onOpenSync)
            )

            // About
            SectionHeader(stringResource(R.string.settings_about))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.about_title)) },
                supportingContent = { Text(stringResource(R.string.app_tagline)) },
                modifier = Modifier.clickable(onClick = onOpenAbout)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
    HorizontalDivider()
}
