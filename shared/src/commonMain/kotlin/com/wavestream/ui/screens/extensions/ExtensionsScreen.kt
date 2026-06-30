package com.wavestream.ui.screens.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.wavestream.WaveAppInit
import com.wavestream.stremio.StremioAddonRepository
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extensions screen. Three concerns, in three sections:
 *
 * 1. Loaded plugins — shows what's currently loaded by [PluginManager] and
 *    a button to rescan the extensions directory.
 * 2. CloudStream repositories — add/remove CS repo URLs. Adding a repo URL
 *    parses it via [RepositoryManager.parseRepoUrl] and lists the plugins
 *    it advertises (rendering happens once the repo is added).
 * 3. Stremio addons — add/remove Stremio addon URLs which become
 *    [com.wavestream.stremio.StremioProviderAdapter] providers.
 */
@Composable
fun ExtensionsScreen(
    onRescanRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bootState by WaveAppInit.bootState.collectAsState()
    var plugins by remember { mutableStateOf<List<String>>(emptyList()) }
    var repos by remember { mutableStateOf<List<RepositoryData>>(emptyList()) }
    var stremioAddons by remember { mutableStateOf<List<String>>(emptyList()) }
    var newRepoUrl by remember { mutableStateOf("") }
    var newStremioUrl by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshAll() {
        plugins = PluginManager.plugins.values.map { it.filename?.substringAfterLast('/') ?: "?" }
        repos = RepositoryManager.getRepositories().toList()
        stremioAddons = StremioAddonRepository.listAddons()
    }

    // Initial load
    remember { refreshAll(); Unit }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader(
                title = "Loaded plugins",
                subtitle = "${plugins.size} plugins loaded · boot: ${bootState.stage}",
                action = {
                    IconButton(onClick = {
                        onRescanRequested()
                        statusMessage = "Rescanning…"
                        scope.launch {
                            delay(1500) // give PluginManager time to reload
                            refreshAll()
                            statusMessage = "Reloaded ${plugins.size} plugins"
                        }
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Rescan")
                    }
                },
            )
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (plugins.isEmpty()) {
                        Text(
                            text = "No plugins loaded. Add a repository below to install extensions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        plugins.forEach { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "CloudStream repositories")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newRepoUrl,
                    onValueChange = { newRepoUrl = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("https://cs.repo/example") },
                    singleLine = true,
                )
                Button(onClick = {
                    val candidate = newRepoUrl.trim()
                    if (candidate.isEmpty()) return@Button
                    scope.launch {
                        statusMessage = "Resolving repo…"
                        val resolved = withContext(Dispatchers.Default) {
                            RepositoryManager.parseRepoUrl(candidate)
                        }
                        if (resolved == null) {
                            statusMessage = "Invalid repository URL"
                            return@launch
                        }
                        val pluginsList = withContext(Dispatchers.Default) {
                            RepositoryManager.getRepoPlugins(resolved)
                        }
                        if (pluginsList.isNullOrEmpty()) {
                            statusMessage = "Repository has no plugins or failed to load"
                            return@launch
                        }
                        // Save the repo descriptor with display metadata
                        val repoData = RepositoryData(
                            url = resolved,
                            name = candidate,
                            pluginLists = pluginsList.map { it.first }.distinct(),
                        )
                        RepositoryManager.addRepository(repoData)
                        statusMessage = "Added repo with ${pluginsList.size} plugins"
                        newRepoUrl = ""
                        refreshAll()
                    }
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("Add")
                }
            }
            if (repos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                repos.forEach { repo ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = repo.name ?: repo.url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = repo.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    RepositoryManager.removeRepository(repo)
                                    refreshAll()
                                }
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Stremio addons")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newStremioUrl,
                    onValueChange = { newStremioUrl = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("https://example.com/stremio/addon") },
                    singleLine = true,
                )
                Button(onClick = {
                    val candidate = newStremioUrl.trim()
                    if (candidate.isEmpty()) return@Button
                    StremioAddonRepository.addAddon(candidate)
                    StremioAddonRepository.syncProviders()
                    newStremioUrl = ""
                    refreshAll()
                    statusMessage = "Stremio addon added"
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add")
                }
            }
            if (stremioAddons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                stremioAddons.forEach { url ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = url,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(onClick = {
                                StremioAddonRepository.removeAddon(url)
                                StremioAddonRepository.syncProviders()
                                refreshAll()
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }

        statusMessage?.let {
            item {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action()
    }
}
