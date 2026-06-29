package com.wavestream.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen — mirrors CloudStream's SettingsFragment.
 * This is a main tab (accessible via bottom navigation).
 */
@Composable
fun SettingsScreen(
    onNavigateToExtensions: () -> Unit,
) {
    val settingsItems = listOf(
        SettingsItem("General", Icons.Filled.Settings, "Language, layout, homepage"),
        SettingsItem("UI", Icons.Filled.Palette, "Theme, poster style, animations"),
        SettingsItem("Player", Icons.Filled.PlayCircle, "Quality, subtitles, gestures, skip intro"),
        SettingsItem("Providers", Icons.Filled.Cloud, "Enable/disable specific providers"),
        SettingsItem("Extensions", Icons.Filled.Extension, "Manage plugins + Stremio addons") {
            onNavigateToExtensions()
        },
        SettingsItem("Updates", Icons.Filled.Update, "Auto-update plugins, app updates"),
        SettingsItem("Account", Icons.Filled.AccountCircle, "MAL, AniList, Trakt sync"),
        SettingsItem("Backup", Icons.Filled.Backup, "Backup + restore app data"),
        SettingsItem("About", Icons.Filled.Info, "Version, license, credits"),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(settingsItems) { item ->
            SettingsRow(item)
        }
    }
}

private data class SettingsItem(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val onClick: () -> Unit = {},
)

@Composable
private fun SettingsRow(item: SettingsItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
