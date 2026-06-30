package com.wavestream.ui.screens.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.wavestream.WaveAppInit
import com.wavestream.platform.wavePlatform
import com.wavestream.stremio.StremioAddonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extensions screen. Three sections:
 *
 * 1. Installed plugins — shows .cs3/.jar files in the Extensions directory
 *    with a rescan button.
 * 2. CloudStream repositories — add/remove CS repo URLs. Each repo expands
 *    to show its plugins with Install/Uninstall buttons. Installing
 *    downloads the .cs3 file and triggers a plugin reload.
 * 3. Stremio addons — add/remove Stremio addon URLs.
 *
 * This is the main "app store" surface of Wavestream. Users browse repos,
 * install plugins, and the providers show up in Home/Search immediately.
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

    // Cache of plugins per repo — fetched when the repo is first expanded.
    val repoPlugins = remember { mutableStateListOf<Pair<String, List<SitePlugin>>>() }
    var expandedRepoUrl by remember { mutableStateOf<String?>(null) }
    var loadingRepoPlugins by remember { mutableStateOf(false) }
    var installingPluginUrl by remember { mutableStateOf<String?>(null) }

    // Track which plugins are installed (by file basename in Extensions dir)
    var installedPluginNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun refreshAll() {
        plugins = PluginManager.plugins.values.map { it.filename?.substringAfterLast('/') ?: "?" }
        repos = RepositoryManager.getRepositories().toList()
        stremioAddons = StremioAddonRepository.listAddons()
        installedPluginNames = wavePlatform.extensionsDir
            .listFiles { f -> f.extension in setOf("cs3", "jar", "ws3") }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()
    }

    // Refresh on boot state change and on first composition
    LaunchedEffect(bootState.stage) {
        if (bootState.stage.isReady || bootState.stage == WaveAppInit.BootStage.FAILED) {
            refreshAll()
        }
    }

    fun loadRepoPlugins(url: String) {
        scope.launch {
            loadingRepoPlugins = true
            try {
                val pluginsList = withContext(Dispatchers.Default) {
                    RepositoryManager.getRepoPlugins(url)
                }
                if (pluginsList != null) {
                    // Replace any existing entry for this repo URL
                    repoPlugins.removeAll { it.first == url }
                    repoPlugins.add(url to pluginsList.map { it.second })
                } else {
                    statusMessage = "Could not parse repo at $url"
                }
            } catch (e: Throwable) {
                statusMessage = "Error loading repo: ${e.message}"
            } finally {
                loadingRepoPlugins = false
            }
        }
    }

    fun installPlugin(plugin: SitePlugin) {
        scope.launch {
            installingPluginUrl = plugin.url
            try {
                val fileName = plugin.name + ".cs3"
                val destFile = File(wavePlatform.extensionsDir, fileName)
                val downloaded = withContext(Dispatchers.Default) {
                    RepositoryManager.downloadPluginToFile(
                        pluginUrl = plugin.url,
                        file = destFile,
                        expectedFileHash = plugin.fileHash,
                        tempDir = wavePlatform.extensionsDir,
                    )
                }
                if (downloaded != null) {
                    // Load the plugin immediately
                    withContext(Dispatchers.Default) {
                        PluginManager.loadPlugin(downloaded)
                    }
                    statusMessage = "Installed: ${plugin.name}"
                    refreshAll()
                } else {
                    statusMessage = "Failed to download: ${plugin.name}"
                }
            } catch (e: Throwable) {
                statusMessage = "Install error: ${e.message}"
            } finally {
                installingPluginUrl = null
            }
        }
    }

    fun uninstallPlugin(plugin: SitePlugin) {
        scope.launch {
            val fileName = plugin.name + ".cs3"
            val file = File(wavePlatform.extensionsDir, fileName)
            if (file.exists()) {
                PluginManager.unloadPlugin(file.absolutePath)
                file.delete()
                statusMessage = "Uninstalled: ${plugin.name}"
                refreshAll()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Section 1: Installed plugins ──────────────────────────────
        item {
            SectionHeader(
                title = "Installed plugins",
                subtitle = "${plugins.size} plugins loaded · boot: ${bootState.stage}",
                action = {
                    IconButton(onClick = {
                        onRescanRequested()
                        statusMessage = "Rescanning…"
                        scope.launch {
                            delay(1500)
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
                            text = "No plugins installed. Add a repository below and install plugins from it.",
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

        // ── Section 2: CloudStream repositories ───────────────────────
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
                    placeholder = { Text("https://cs.repo/milkman or plugins.json URL") },
                    singleLine = true,
                )
                Button(onClick = {
                    val candidate = newRepoUrl.trim()
                    if (candidate.isEmpty()) return@Button
                    scope.launch {
                        statusMessage = "Resolving repo…"
                        val resolved = withContext(Dispatchers.Default) {
                            RepositoryManager.parseRepoUrl(candidate)
                        } ?: candidate
                        val pluginsList = withContext(Dispatchers.Default) {
                            RepositoryManager.getRepoPlugins(resolved)
                        }
                        if (pluginsList.isNullOrEmpty()) {
                            statusMessage = "No plugins found at this URL"
                            return@launch
                        }
                        val repoData = RepositoryData(
                            url = resolved,
                            name = candidate.substringAfterLast("/").substringBefore(".json"),
                            pluginLists = pluginsList.map { it.first }.distinct(),
                        )
                        RepositoryManager.addRepository(repoData)
                        statusMessage = "Added repo with ${pluginsList.size} plugins"
                        newRepoUrl = ""
                        refreshAll()
                    }
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add")
                }
            }
        }

        // Render each repo as an expandable card
        items(repos, key = { it.url }) { repo ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repo.name ?: repo.url.substringAfterLast("/"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = repo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            expandedRepoUrl = if (expandedRepoUrl == repo.url) null else repo.url
                            if (expandedRepoUrl == repo.url && repoPlugins.none { it.first == repo.url }) {
                                loadRepoPlugins(repo.url)
                            }
                        }) {
                            Icon(
                                if (expandedRepoUrl == repo.url) Icons.Outlined.Check else Icons.Outlined.CloudDownload,
                                contentDescription = "Browse",
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

                    // Expanded: show plugins from this repo
                    if (expandedRepoUrl == repo.url) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (loadingRepoPlugins) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Loading plugins…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            val pluginsList = repoPlugins.find { it.first == repo.url }?.second ?: emptyList()
                            if (pluginsList.isEmpty()) {
                                Text(
                                    text = "No plugins in this repo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp),
                                )
                            } else {
                                pluginsList.forEach { plugin ->
                                    PluginRow(
                                        plugin = plugin,
                                        isInstalled = plugin.name in installedPluginNames,
                                        isInstalling = installingPluginUrl == plugin.url,
                                        onInstall = { installPlugin(plugin) },
                                        onUninstall = { uninstallPlugin(plugin) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Section 3: Stremio addons ─────────────────────────────────
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
                    placeholder = { Text("https://v3-cinemeta.strem.io/manifest.json") },
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
        }

        items(stremioAddons, key = { it }) { url ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = url,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
private fun PluginRow(
    plugin: SitePlugin,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = plugin.description ?: "v${plugin.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isInstalling) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else if (isInstalled) {
            OutlinedButton(onClick = onUninstall) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Remove")
            }
        } else {
            Button(onClick = onInstall) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Install")
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
