package com.wavestream.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.WaveAppInit
import com.wavestream.platform.wavePlatform
import androidx.compose.runtime.collectAsState

/**
 * Settings screen. Bare-bones set of toggles persisted via
 * [wavePlatform.preferences]. A full settings implementation would
 * mirror CloudStream 3's categories (General / Player / Subtitles /
 * Providers / Account / Info) but for the reproduction guide we expose
 * only the essentials.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val bootState by WaveAppInit.bootState.collectAsState()
    var jsdelivr by remember {
        mutableStateOf(wavePlatform.preferences.getBool("wavestream.jsdelivr", false))
    }
    var autoRescan by remember {
        mutableStateOf(wavePlatform.preferences.getBool("wavestream.auto_rescan", true))
    }
    var preferStremioStreams by remember {
        mutableStateOf(wavePlatform.preferences.getBool("wavestream.prefer_stremio", false))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSectionHeader("General")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow(
                        title = "Use jsDelivr proxy for GitHub",
                        subtitle = "Faster downloads of CS repositories hosted on raw.githubusercontent.com.",
                        checked = jsdelivr,
                        onCheckedChange = { v ->
                            jsdelivr = v
                            wavePlatform.preferences.putBool("wavestream.jsdelivr", v)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingRow(
                        title = "Auto-rescan extensions on boot",
                        subtitle = "Re-scan the Extensions directory every time the app starts.",
                        checked = autoRescan,
                        onCheckedChange = { v ->
                            autoRescan = v
                            wavePlatform.preferences.putBool("wavestream.auto_rescan", v)
                        },
                    )
                }
            }
        }

        item {
            SettingsSectionHeader("Streaming")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow(
                        title = "Prefer Stremio streams",
                        subtitle = "When both CS and Stremio sources are available, prefer Stremio.",
                        checked = preferStremioStreams,
                        onCheckedChange = { v ->
                            preferStremioStreams = v
                            wavePlatform.preferences.putBool("wavestream.prefer_stremio", v)
                        },
                    )
                }
            }
        }

        item {
            SettingsSectionHeader("About")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Wavestream",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "A CloudStream 3 fork with a Compose Multiplatform UI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Boot stage: ${bootState.stage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Plugins loaded: ${bootState.pluginsLoaded}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Data dir: ${wavePlatform.dataDir.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
