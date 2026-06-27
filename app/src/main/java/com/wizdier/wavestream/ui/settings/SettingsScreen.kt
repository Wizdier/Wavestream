package com.wizdier.wavestream.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenRepos: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProviders: () -> Unit = onOpenRepos,
    onOpenDownloads: () -> Unit = {},
    onOpenSearch: () -> Unit = {}
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var swipeGestures by remember { mutableStateOf(true) }
    var autoPip by remember { mutableStateOf(true) }
    var skipIntro by remember { mutableStateOf(true) }
    var autoPlayNext by remember { mutableStateOf(true) }
    var prefetch by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Extensions & Providers ─────────────────────────────────
            SectionHeader("EXTENSIONS & PROVIDERS", icon = Icons.Outlined.Extension)
            SettingsRow(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.settings_providers),
                subtitle = "Manage installed provider extensions",
                onClick = onOpenProviders
            )
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Installed extensions",
                subtitle = "0 providers active",
                onClick = onOpenProviders
            )
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Downloads",
                subtitle = "Manage downloaded videos",
                onClick = onOpenDownloads
            )
            HorizontalDivider()

            // ─── Library ────────────────────────────────────────────────
            SectionHeader("LIBRARY", icon = Icons.Outlined.Favorite)
            SettingsRow(
                icon = Icons.Outlined.Favorite,
                title = stringResource(R.string.favorites_title),
                subtitle = "Your watchlists and lists",
                onClick = onOpenFavorites
            )
            SettingsRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_title),
                subtitle = "Recently watched",
                onClick = onOpenHistory
            )
            HorizontalDivider()

            // ─── Search ─────────────────────────────────────────────────
            SectionHeader("SEARCH", icon = Icons.Outlined.Search)
            SettingsRow(
                icon = Icons.Outlined.Search,
                title = "Search providers",
                subtitle = "Select which providers to search",
                onClick = onOpenSearch
            )
            HorizontalDivider()

            // ─── Appearance ─────────────────────────────────────────────
            SectionHeader("APPEARANCE", icon = Icons.Outlined.Palette)
            SettingsRow(
                icon = Icons.Outlined.Brightness6,
                title = stringResource(R.string.settings_dark_mode)
            )
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = "Material You wallpaper-derived colors",
                trailing = {
                    Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
                }
            )
            HorizontalDivider()

            // ─── Player ─────────────────────────────────────────────────
            SectionHeader("PLAYER", icon = Icons.Outlined.PlayCircle)
            SettingsRow(
                icon = Icons.Outlined.PlayCircle,
                title = stringResource(R.string.settings_player_swipe),
                subtitle = "Brightness · volume · seek",
                trailing = {
                    Switch(checked = swipeGestures, onCheckedChange = { swipeGestures = it })
                }
            )
            SettingsRow(
                title = stringResource(R.string.settings_player_pip),
                subtitle = "Auto Picture-in-Picture on home press",
                trailing = {
                    Switch(checked = autoPip, onCheckedChange = { autoPip = it })
                }
            )
            SettingsRow(
                title = stringResource(R.string.settings_player_skip_intro),
                subtitle = "Show skip intro button when detected",
                trailing = {
                    Switch(checked = skipIntro, onCheckedChange = { skipIntro = it })
                }
            )
            SettingsRow(
                title = "Auto-play next episode",
                subtitle = "Continue to the next episode automatically",
                trailing = {
                    Switch(checked = autoPlayNext, onCheckedChange = { autoPlayNext = it })
                }
            )
            SettingsRow(
                title = "Preload next episode",
                subtitle = "Buffer next episode while watching",
                trailing = {
                    Switch(checked = prefetch, onCheckedChange = { prefetch = it })
                }
            )
            HorizontalDivider()

            // ─── Sync ───────────────────────────────────────────────────
            SectionHeader("SYNC", icon = Icons.Outlined.Sync)
            SettingsRow(
                icon = Icons.Outlined.Sync,
                title = stringResource(R.string.sync_trakt) + " / " + stringResource(R.string.sync_mal),
                subtitle = "Sync watch progress and favorites",
                onClick = onOpenSync
            )
            HorizontalDivider()

            // ─── Advanced ───────────────────────────────────────────────
            SectionHeader("ADVANCED", icon = Icons.Outlined.Settings)
            SettingsRow(
                icon = Icons.Outlined.Tune,
                title = "Default video quality",
                subtitle = "Auto"
            )
            SettingsRow(
                icon = Icons.Outlined.CloudDone,
                title = "Cache",
                subtitle = "Clear image and subtitle cache"
            )
            HorizontalDivider()

            // ─── About ──────────────────────────────────────────────────
            SectionHeader("ABOUT", icon = Icons.Outlined.Info)
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.app_tagline),
                onClick = onOpenAbout
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}
