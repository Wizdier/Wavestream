package com.wavestream.ui.screens.settings

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

@Composable
fun SettingsScreen(
    onNavigateToExtensions: () -> Unit,
) {
    var openDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val settingsItems = listOf(
        SettingsItem("Extensions", Icons.Filled.Extension, "Manage plugins, repos") {
            onNavigateToExtensions()
        },
        SettingsItem("General", Icons.Filled.Settings, "Language, layout, homepage") {
            openDialog = SettingsDialog.General
        },
        SettingsItem("UI", Icons.Filled.Palette, "Theme, poster style, animations") {
            openDialog = SettingsDialog.UI
        },
        SettingsItem("Player", Icons.Filled.PlayCircle, "Quality, subtitles, gestures") {
            openDialog = SettingsDialog.Player
        },
        SettingsItem("Updates", Icons.Filled.Update, "Check for app and plugin updates") {
            openDialog = SettingsDialog.Updates
        },
        SettingsItem("About", Icons.Filled.Info, "Wavestream v1.0.0") {
            openDialog = SettingsDialog.About
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(settingsItems) { item ->
            SettingsRow(item)
        }
    }

    when (openDialog) {
        null -> {}
        SettingsDialog.General -> SimpleInfoDialog(
            title = "General Settings",
            message = "Language, layout, and homepage preferences.\n\nThese settings will be configurable in a future update.",
            onDismiss = { openDialog = null },
        )
        SettingsDialog.UI -> SimpleInfoDialog(
            title = "UI Settings",
            message = "Theme, poster style, and animation preferences.\n\nDark theme is currently always enabled.",
            onDismiss = { openDialog = null },
        )
        SettingsDialog.Player -> SimpleInfoDialog(
            title = "Player Settings",
            message = "Default quality, subtitle preferences, gestures, and skip intro.",
            onDismiss = { openDialog = null },
        )
        SettingsDialog.Updates -> SimpleInfoDialog(
            title = "Updates",
            message = "App updates are checked automatically from GitHub Releases.\nPlugin updates are checked when repositories are refreshed in Extensions.",
            onDismiss = { openDialog = null },
        )
        SettingsDialog.About -> SimpleInfoDialog(
            title = "About Wavestream",
            message = "Wavestream v1.0.0\n\nA modern media center — a fork of CloudStream 3 with a Compose Multiplatform UI.\n\nSupports CloudStream extensions (.cs3/.jar) and Stremio addons.\n\nLicensed under MIT.",
            onDismiss = { openDialog = null },
        )
    }
}

private enum class SettingsDialog { General, UI, Player, Updates, About }

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

@Composable
private fun SimpleInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
