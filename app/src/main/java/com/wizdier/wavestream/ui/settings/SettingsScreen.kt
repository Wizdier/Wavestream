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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onOpenSearch: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val swipeGestures by viewModel.swipeGestures.collectAsState()
    val autoPip by viewModel.autoPip.collectAsState()
    val skipIntro by viewModel.skipIntro.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val preloadNext by viewModel.preloadNext.collectAsState()
    val subtitleLang by viewModel.subtitleLang.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val autoDownloadNew by viewModel.autoDownloadNew.collectAsState()
    val enableNsfw by viewModel.enableNsfw.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Extensions & Providers ─────────────────────────────
            SectionHeader("EXTENSIONS & PROVIDERS", Icons.Outlined.Extension)
            SettingsRow(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.settings_providers),
                subtitle = "Manage installed provider extensions",
                onClick = onOpenProviders
            )
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Installed extensions",
                subtitle = "View and manage active providers",
                onClick = onOpenProviders
            )
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Downloads",
                subtitle = "Manage downloaded videos",
                onClick = onOpenDownloads
            )
            HorizontalDivider()

            // ─── Library ────────────────────────────────────────────
            SectionHeader("LIBRARY", Icons.Outlined.Favorite)
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

            // ─── Search ─────────────────────────────────────────────
            SectionHeader("SEARCH", Icons.Outlined.Search)
            SettingsRow(
                icon = Icons.Outlined.Search,
                title = "Search providers",
                subtitle = "Select which providers to search",
                onClick = onOpenSearch
            )
            HorizontalDivider()

            // ─── Appearance ─────────────────────────────────────────
            SectionHeader("APPEARANCE", Icons.Outlined.Palette)
            SettingsRow(
                icon = Icons.Outlined.Brightness6,
                title = stringResource(R.string.settings_dark_mode)
            )
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = "Material You wallpaper-derived colors",
                trailing = { Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) }) }
            )
            HorizontalDivider()

            // ─── Player ─────────────────────────────────────────────
            SectionHeader("PLAYER", Icons.Outlined.PlayCircle)
            SettingsRow(
                icon = Icons.Outlined.PlayCircle,
                title = stringResource(R.string.settings_player_swipe),
                subtitle = "Brightness · volume · seek",
                trailing = { Switch(checked = swipeGestures, onCheckedChange = { viewModel.setSwipeGestures(it) }) }
            )
            SettingsRow(
                title = stringResource(R.string.settings_player_pip),
                subtitle = "Auto Picture-in-Picture on home press",
                trailing = { Switch(checked = autoPip, onCheckedChange = { viewModel.setAutoPip(it) }) }
            )
            SettingsRow(
                title = stringResource(R.string.settings_player_skip_intro),
                subtitle = "Show skip intro button when detected",
                trailing = { Switch(checked = skipIntro, onCheckedChange = { viewModel.setSkipIntro(it) }) }
            )
            SettingsRow(
                title = "Auto-play next episode",
                subtitle = "Continue to the next episode automatically",
                trailing = { Switch(checked = autoPlayNext, onCheckedChange = { viewModel.setAutoPlayNext(it) }) }
            )
            SettingsRow(
                title = "Preload next episode",
                subtitle = "Buffer next episode while watching",
                trailing = { Switch(checked = preloadNext, onCheckedChange = { viewModel.setPreloadNext(it) }) }
            )
            HorizontalDivider()

            // ─── Subtitles ──────────────────────────────────────────
            SectionHeader("SUBTITLES", Icons.Outlined.Subtitles)
            SettingsRow(
                icon = Icons.Outlined.Subtitles,
                title = "Preferred language",
                subtitle = subtitleLang.uppercase(),
                onClick = { /* TODO: language picker dialog */ }
            )
            SettingsRow(
                title = "Font size",
                subtitle = "${subtitleSize}sp",
                onClick = { /* TODO: slider dialog */ }
            )
            HorizontalDivider()

            // ─── Sync ───────────────────────────────────────────────
            SectionHeader("SYNC", Icons.Outlined.Sync)
            SettingsRow(
                icon = Icons.Outlined.Sync,
                title = stringResource(R.string.sync_trakt) + " / " + stringResource(R.string.sync_mal),
                subtitle = "Sync watch progress and favorites",
                onClick = onOpenSync
            )
            HorizontalDivider()

            // ─── Advanced ───────────────────────────────────────────
            SectionHeader("ADVANCED", Icons.Outlined.Settings)
            SettingsRow(
                icon = Icons.Outlined.Tune,
                title = "Default video quality",
                subtitle = "Auto"
            )
            SettingsRow(
                title = "Auto-download new episodes",
                subtitle = "Download new episodes as they air",
                trailing = { Switch(checked = autoDownloadNew, onCheckedChange = { viewModel.setAutoDownloadNew(it) }) }
            )
            SettingsRow(
                title = "NSFW content",
                subtitle = "Show adult content in search results",
                trailing = { Switch(checked = enableNsfw, onCheckedChange = { viewModel.setEnableNsfw(it) }) }
            )
            HorizontalDivider()

            // ─── About ──────────────────────────────────────────────
            SectionHeader("ABOUT", Icons.Outlined.Info)
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}
