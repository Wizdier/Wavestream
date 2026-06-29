package com.wavestream.ui.screens.extensions

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.wavestream.RepositoryStore
import com.wavestream.WaveAppInit
import com.wavestream.stremio.StremioAddonRepository
import com.wavestream.stremio.StoredStremioAddon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showAddAddonDialog by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var newAddonUrl by remember { mutableStateOf("") }
    var expandedRepoUrl by remember { mutableStateOf<String?>(null) }
    val pluginsDir = remember { WaveAppInit.getDefaultPluginsDir() }

    val repos = remember { mutableStateListOf<RepositoryData>() }
    val repoPlugins = remember { mutableStateMapOf<String, List<SitePlugin>>() }
    val installedPlugins = remember { mutableStateListOf<java.io.File>() }
    val stremioAddons by StremioAddonRepository.addons.collectAsState()
    val stremioManifests by StremioAddonRepository.manifests.collectAsState()
    val initState by WaveAppInit.initState.collectAsState()
    val logs by WaveAppInit.logs.collectAsState()

    LaunchedEffect(Unit) {
        repos.clear()
        repos.addAll(RepositoryStore.getRepositories())
        refreshInstalledPlugins(installedPlugins, pluginsDir)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            WaveAppInit.refreshRepositories(pluginsDir)
                            snackbarHost.showSnackbar("Refreshing repositories...")
                            // Refresh installed plugins list
                            refreshInstalledPlugins(installedPlugins, pluginsDir)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Init status banner
            item { InitStatusBanner(initState) }

            // =========== Repositories ===========
            item {
                SectionHeader(
                    title = "Repositories",
                    subtitle = "${repos.size} added",
                    actionText = "Add Repo",
                    onAction = { showAddRepoDialog = true },
                )
            }

            if (repos.isEmpty()) {
                item {
                    EmptyHint(
                        icon = Icons.Filled.CloudDownload,
                        text = "No repositories added. Tap 'Add Repo' to add a CloudStream repository URL.",
                    )
                }
            } else {
                items(repos, key = { it.url }) { repo ->
                    val plugins = repoPlugins[repo.url] ?: emptyList()
                    RepoRow(
                        repo = repo,
                        plugins = plugins,
                        isExpanded = expandedRepoUrl == repo.url,
                        pluginsDir = pluginsDir,
                        onToggleExpand = {
                            expandedRepoUrl = if (expandedRepoUrl == repo.url) null else repo.url
                            if (expandedRepoUrl == repo.url && plugins.isEmpty()) {
                                scope.launch {
                                    snackbarHost.showSnackbar("Fetching ${repo.url}...")
                                    val fetched = withContext(Dispatchers.Default) {
                                        runCatching { RepositoryManager.getRepoPlugins(repo.url) }.getOrNull()
                                    }
                                    if (fetched == null) {
                                        snackbarHost.showSnackbar("Failed to fetch repository")
                                    } else {
                                        repoPlugins[repo.url] = fetched
                                        snackbarHost.showSnackbar("Found ${fetched.size} plugins")
                                    }
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                RepositoryStore.removeRepository(repo.url)
                                repos.remove(repo)
                                // Also delete downloaded plugins for this repo
                                val repoFolder = File(pluginsDir, RepositoryManager.getRepoFolderName(repo.url))
                                if (repoFolder.exists()) {
                                    repoFolder.listFiles()?.forEach { f ->
                                        PluginManager.unloadPlugin(f.absolutePath)
                                        f.delete()
                                    }
                                    repoFolder.delete()
                                }
                                refreshInstalledPlugins(installedPlugins, pluginsDir)
                                snackbarHost.showSnackbar("Removed repository")
                            }
                        },
                        onInstallPlugin = { plugin ->
                            scope.launch {
                                val repoFolder = File(pluginsDir, RepositoryManager.getRepoFolderName(repo.url))
                                val pluginFile = File(repoFolder, RepositoryManager.getPluginFileName(plugin.internalName))
                                val downloaded = withContext(Dispatchers.Default) {
                                    RepositoryManager.downloadPluginToFile(
                                        pluginUrl = plugin.bestUrlForPlatform(),
                                        targetFile = pluginFile,
                                        expectedFileHash = plugin.bestHashForPlatform(),
                                    )
                                }
                                if (downloaded != null) {
                                    val loaded = withContext(Dispatchers.Default) {
                                        PluginManager.loadPlugin(downloaded, sourceUrl = repo.url)
                                    }
                                    snackbarHost.showSnackbar(
                                        if (loaded) "Installed ${plugin.name}" else "Failed to load ${plugin.name}"
                                    )
                                    refreshInstalledPlugins(installedPlugins, pluginsDir)
                                } else {
                                    snackbarHost.showSnackbar("Failed to download ${plugin.name}")
                                }
                            }
                        },
                        onUninstallPlugin = { plugin ->
                            scope.launch {
                                val repoFolder = File(pluginsDir, RepositoryManager.getRepoFolderName(repo.url))
                                val pluginFile = File(repoFolder, RepositoryManager.getPluginFileName(plugin.internalName))
                                if (pluginFile.exists()) {
                                    PluginManager.unloadPlugin(pluginFile.absolutePath)
                                    pluginFile.delete()
                                    refreshInstalledPlugins(installedPlugins, pluginsDir)
                                    snackbarHost.showSnackbar("Removed ${plugin.name}")
                                }
                            }
                        },
                    )
                }
            }

            // =========== Stremio Addons ===========
            item {
                SectionHeader(
                    title = "Stremio Addons",
                    subtitle = "${stremioAddons.size} added",
                    actionText = "Add Addon",
                    onAction = { showAddAddonDialog = true },
                )
            }

            if (stremioAddons.isEmpty()) {
                item {
                    EmptyHint(
                        icon = Icons.Filled.Extension,
                        text = "No Stremio addons. Add one by manifest URL (https://.../manifest.json) to start streaming.",
                    )
                }
            } else {
                items(stremioAddons, key = { it.manifestUrl }) { addon ->
                    val manifest = stremioManifests[addon.manifestUrl]
                    AddonRow(
                        addon = addon,
                        manifestName = manifest?.name ?: addon.name,
                        catalogCount = manifest?.catalogs?.size ?: 0,
                        onToggle = { StremioAddonRepository.toggleAddon(addon.manifestUrl) },
                        onDelete = {
                            StremioAddonRepository.removeAddon(addon.manifestUrl)
                            scope.launch { snackbarHost.showSnackbar("Removed addon") }
                        },
                    )
                }
            }

            // =========== Active Providers ===========
            item {
                SectionHeader(
                    title = "Active Providers",
                    subtitle = "${APIHolder.allProviders.size} loaded",
                    actionText = null,
                    onAction = null,
                )
            }
            val providers = APIHolder.allProviders.toList()
            if (providers.isEmpty()) {
                item { EmptyHint(icon = Icons.Filled.SearchOff, text = "No providers loaded. Add a repository to get started.") }
            } else {
                items(providers, key = { it.name + it.mainUrl }) { provider ->
                    ProviderRow(provider.name, provider.mainUrl, provider.hasMainPage)
                }
            }

            // =========== Installed Plugin Files ===========
            if (installedPlugins.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Installed Plugin Files",
                        subtitle = "${installedPlugins.size} files",
                        actionText = null,
                        onAction = null,
                    )
                }
                items(installedPlugins, key = { it.absolutePath }) { pluginFile ->
                    InstalledPluginRow(
                        pluginFile = pluginFile,
                        onDelete = {
                            scope.launch {
                                PluginManager.unloadPlugin(pluginFile.absolutePath)
                                pluginFile.delete()
                                refreshInstalledPlugins(installedPlugins, pluginsDir)
                                snackbarHost.showSnackbar("Deleted ${pluginFile.name}")
                            }
                        },
                    )
                }
            }

            // =========== Init Logs ===========
            if (logs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Init Logs",
                        subtitle = "${logs.size} entries",
                        actionText = null,
                        onAction = null,
                    )
                }
                items(logs.takeLast(15).reversed(), key = { it.timestamp.toString() + it.message.hashCode() }) { entry ->
                    val color = when (entry.level) {
                        com.wavestream.LogLevel.Error -> MaterialTheme.colorScheme.error
                        com.wavestream.LogLevel.Warning -> MaterialTheme.colorScheme.tertiary
                        com.wavestream.LogLevel.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "[${entry.level}] ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Repository") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = { newRepoUrl = it },
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://example.com/repo.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: CloudStream repo URLs work too (cloudstreamrepo://... or https://cs.repo/...)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = newRepoUrl.trim()
                    if (url.isNotBlank()) {
                        scope.launch {
                            val added = RepositoryStore.addRepository(url)
                            if (added) {
                                repos.clear()
                                repos.addAll(RepositoryStore.getRepositories())
                                snackbarHost.showSnackbar("Repository added. Tap it to fetch plugins.")
                            } else {
                                snackbarHost.showSnackbar("Repository already exists")
                            }
                            val fetched = withContext(Dispatchers.Default) {
                                runCatching { RepositoryManager.getRepoPlugins(url) }.getOrNull()
                            }
                            if (fetched != null) {
                                repoPlugins[RepositoryManager.parseRepoUrl(url)] = fetched
                                snackbarHost.showSnackbar("Found ${fetched.size} plugins in repository")
                                expandedRepoUrl = RepositoryManager.parseRepoUrl(url)
                            }
                        }
                    }
                    newRepoUrl = ""
                    showAddRepoDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddRepoDialog = false }) { Text("Cancel") } },
        )
    }

    if (showAddAddonDialog) {
        AlertDialog(
            onDismissRequest = { showAddAddonDialog = false },
            title = { Text("Add Stremio Addon") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newAddonUrl,
                        onValueChange = { newAddonUrl = it },
                        label = { Text("Manifest URL") },
                        placeholder = { Text("https://example.com/manifest.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: stremio:// URLs also work. Find addons at https://stremio-addons.netlify.app/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = newAddonUrl.trim()
                    if (url.isNotBlank()) {
                        scope.launch {
                            snackbarHost.showSnackbar("Adding addon...")
                            val result = withContext(Dispatchers.Default) {
                                StremioAddonRepository.addAddon(url)
                            }
                            result.onSuccess {
                                snackbarHost.showSnackbar("Added: ${it.name} (${it.catalogs.size} catalogs)")
                            }.onFailure {
                                snackbarHost.showSnackbar("Failed: ${it.message}")
                            }
                        }
                    }
                    newAddonUrl = ""
                    showAddAddonDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddAddonDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun refreshInstalledPlugins(list: MutableList<java.io.File>, pluginsDir: File) {
    list.clear()
    if (pluginsDir.exists()) {
        pluginsDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("jar", "ws3", "cs3") }
            .forEach { list.add(it) }
    }
}

@Composable
private fun InitStatusBanner(initState: com.wavestream.InitState) {
    val (text, color) = when (initState) {
        is com.wavestream.InitState.Loading -> Pair(initState.message, MaterialTheme.colorScheme.primaryContainer)
        is com.wavestream.InitState.Ready -> Pair("${initState.providerCount} providers • ${initState.extractorCount} extractors ready", MaterialTheme.colorScheme.secondaryContainer)
        is com.wavestream.InitState.Error -> Pair("Error: ${initState.message}", MaterialTheme.colorScheme.errorContainer)
        com.wavestream.InitState.SafeMode -> Pair("Safe mode active", MaterialTheme.colorScheme.tertiaryContainer)
        com.wavestream.InitState.Idle -> Pair("Initializing...", MaterialTheme.colorScheme.surfaceVariant)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = color,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (initState is com.wavestream.InitState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?, actionText: String?, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun EmptyHint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RepoRow(
    repo: RepositoryData,
    plugins: List<SitePlugin>,
    isExpanded: Boolean,
    pluginsDir: File,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    onInstallPlugin: (SitePlugin) -> Unit,
    onUninstallPlugin: (SitePlugin) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(repo.name.ifBlank { "Repository" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (plugins.isNotEmpty()) {
                        Text("${plugins.size} plugins available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        if (isExpanded) "Collapse" else "Expand",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (plugins.isEmpty()) {
                        Text("Tap refresh in top bar to fetch plugins.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        plugins.forEach { plugin ->
                            PluginListRow(
                                plugin = plugin,
                                pluginsDir = pluginsDir,
                                onInstall = { onInstallPlugin(plugin) },
                                onUninstall = { onUninstallPlugin(plugin) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginListRow(
    plugin: SitePlugin,
    pluginsDir: File,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val pluginFile = File(
        File(pluginsDir, RepositoryManager.getRepoFolderName(plugin.repositoryUrl ?: "")),
        RepositoryManager.getPluginFileName(plugin.internalName)
    )
    val isInstalled = pluginFile.exists()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Extension, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plugin.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                if (plugin.status == 0) {
                    AssistChip(onClick = {}, label = { Text("Disabled", style = MaterialTheme.typography.labelSmall) })
                }
            }
            Text(
                buildString {
                    append("v${plugin.version}")
                    plugin.language?.let { append(" • $it") }
                    plugin.tvTypes?.takeIf { it.isNotEmpty() }?.let { append(" • ${it.joinToString(",")}") }
                    plugin.fileSize?.let { append(" • ${it / 1024}KB") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            plugin.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (isInstalled) {
            FilledTonalButton(onClick = onUninstall, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remove", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Button(onClick = onInstall, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Install", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProviderRow(name: String, url: String, hasMainPage: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasMainPage) {
                Icon(Icons.Filled.Home, "Has home page", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InstalledPluginRow(pluginFile: java.io.File, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Inventory2, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pluginFile.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pluginFile.length() / 1024} KB • ${pluginFile.parentFile?.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddonRow(
    addon: StoredStremioAddon,
    manifestName: String,
    catalogCount: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(manifestName.ifBlank { "Stremio Addon" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(addon.manifestUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (catalogCount > 0) {
                    Text("$catalogCount catalogs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Switch(checked = addon.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
