package com.wavestream.ui.screens.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.wavestream.InitState
import com.wavestream.LogLevel
import com.wavestream.RepositoryStore
import com.wavestream.WaveAppInit
import com.wavestream.stremio.StremioAddonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showAddRepo by remember { mutableStateOf(false) }
    var showAddAddon by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var newAddonUrl by remember { mutableStateOf("") }
    var expandedRepo by remember { mutableStateOf<String?>(null) }
    val pluginsDir = remember { WaveAppInit.getDefaultPluginsDir() }
    val repos = remember { mutableStateListOf<RepositoryData>() }
    val repoPlugins = remember { mutableStateMapOf<String, List<SitePlugin>>() }
    val installed = remember { mutableStateListOf<File>() }
    val stremioAddons by StremioAddonRepository.addons.collectAsState()
    val stremioManifests by StremioAddonRepository.manifests.collectAsState()
    val initState by WaveAppInit.initState.collectAsState()
    val logs by WaveAppInit.logs.collectAsState()

    LaunchedEffect(Unit) {
        repos.clear()
        repos.addAll(RepositoryStore.getRepositories())
        installed.clear()
        if (pluginsDir.exists()) {
            pluginsDir.walkTopDown()
                .filter { it.isFile && it.extension in setOf("jar", "ws3", "cs3") }
                .forEach { installed.add(it) }
        }
    }

    val refreshInstalled: () -> Unit = {
        installed.clear()
        if (pluginsDir.exists()) {
            pluginsDir.walkTopDown()
                .filter { it.isFile && it.extension in setOf("jar", "ws3", "cs3") }
                .forEach { installed.add(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Extensions",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            WaveAppInit.refreshRepositories(pluginsDir)
                            refreshInstalled()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { InitStatusCard(initState) }

            item {
                SectionHeader(
                    title = "Repositories",
                    subtitle = "${repos.size} added",
                    onAdd = { showAddRepo = true },
                )
            }

            if (repos.isEmpty()) {
                item {
                    Text(
                        text = "No repos. Tap Add to add a CloudStream repo URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(repos, key = { it.url }) { repo ->
                    RepositoryRow(
                        repo = repo,
                        plugins = repoPlugins[repo.url] ?: emptyList(),
                        isExpanded = expandedRepo == repo.url,
                        pluginsDir = pluginsDir,
                        onToggleExpand = {
                            expandedRepo = if (expandedRepo == repo.url) null else repo.url
                            if (expandedRepo == repo.url && (repoPlugins[repo.url] ?: emptyList()).isEmpty()) {
                                scope.launch {
                                    val fetched = withContext(Dispatchers.Default) {
                                        RepositoryManager.getRepoPlugins(repo.url)
                                    }
                                    if (fetched != null) {
                                        repoPlugins[repo.url] = fetched
                                        snackbar.showSnackbar("Found ${fetched.size} plugins")
                                    }
                                }
                            }
                        },
                        onRemove = {
                            RepositoryStore.removeRepository(repo.url)
                            repos.remove(repo)
                            val folder = File(pluginsDir, RepositoryManager.getRepoFolderName(repo.url))
                            if (folder.exists()) {
                                folder.listFiles()?.forEach {
                                    PluginManager.unloadPlugin(it.absolutePath)
                                    it.delete()
                                }
                                folder.delete()
                            }
                            scope.launch { snackbar.showSnackbar("Removed") }
                        },
                        onInstall = { plugin, file ->
                            scope.launch {
                                val downloaded = withContext(Dispatchers.Default) {
                                    RepositoryManager.downloadPluginToFile(
                                        plugin.bestUrlForPlatform(),
                                        file,
                                        plugin.bestHashForPlatform(),
                                    )
                                }
                                if (downloaded != null) {
                                    withContext(Dispatchers.Default) {
                                        PluginManager.loadPlugin(downloaded, repo.url)
                                    }
                                    installed.add(downloaded)
                                    snackbar.showSnackbar("Installed ${plugin.name}")
                                }
                            }
                        },
                        onUninstall = { file, plugin ->
                            PluginManager.unloadPlugin(file.absolutePath)
                            file.delete()
                            installed.remove(file)
                            scope.launch { snackbar.showSnackbar("Removed ${plugin.name}") }
                        },
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Stremio Addons",
                    subtitle = "${stremioAddons.size} added",
                    onAdd = { showAddAddon = true },
                )
            }

            if (stremioAddons.isEmpty()) {
                item {
                    Text(
                        text = "No Stremio addons. Add one by manifest URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(stremioAddons, key = { it.manifestUrl }) { addon ->
                    val manifest = stremioManifests[addon.manifestUrl]
                    StremioAddonRow(
                        name = manifest?.name ?: addon.name,
                        url = addon.manifestUrl,
                        enabled = addon.enabled,
                        onToggle = { StremioAddonRepository.toggleAddon(addon.manifestUrl) },
                        onRemove = {
                            StremioAddonRepository.removeAddon(addon.manifestUrl)
                            scope.launch { snackbar.showSnackbar("Removed") }
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Active Providers (${APIHolder.allProviders.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            items(APIHolder.allProviders.toList(), key = { it.name + it.mainUrl }) { provider ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = provider.mainUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (logs.isNotEmpty()) {
                item {
                    Text(
                        text = "Init Logs (${logs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(
                    logs.takeLast(15).reversed(),
                    key = { it.timestamp.toString() + it.message.hashCode() },
                ) { entry ->
                    val color = when (entry.level) {
                        LogLevel.Error -> MaterialTheme.colorScheme.error
                        LogLevel.Warning -> MaterialTheme.colorScheme.tertiary
                        LogLevel.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "[${entry.level}] ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showAddRepo) {
        AlertDialog(
            onDismissRequest = { showAddRepo = false },
            title = { Text(text = "Add Repository") },
            text = {
                OutlinedTextField(
                    value = newRepoUrl,
                    onValueChange = { newRepoUrl = it },
                    label = { Text(text = "URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = newRepoUrl.trim()
                    if (u.isNotBlank()) {
                        RepositoryStore.addRepository(u)
                        repos.clear()
                        repos.addAll(RepositoryStore.getRepositories())
                        scope.launch {
                            val fetched = withContext(Dispatchers.Default) {
                                RepositoryManager.getRepoPlugins(u)
                            }
                            if (fetched != null) {
                                repoPlugins[u] = fetched
                                expandedRepo = u
                                snackbar.showSnackbar("Found ${fetched.size} plugins")
                            }
                        }
                    }
                    newRepoUrl = ""
                    showAddRepo = false
                }) {
                    Text(text = "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepo = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (showAddAddon) {
        AlertDialog(
            onDismissRequest = { showAddAddon = false },
            title = { Text(text = "Add Stremio Addon") },
            text = {
                OutlinedTextField(
                    value = newAddonUrl,
                    onValueChange = { newAddonUrl = it },
                    label = { Text(text = "Manifest URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = newAddonUrl.trim()
                    if (u.isNotBlank()) {
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                StremioAddonRepository.addAddon(u)
                            }
                            result.onSuccess { snackbar.showSnackbar("Added: ${it.name}") }
                                .onFailure { snackbar.showSnackbar("Failed: ${it.message}") }
                        }
                    }
                    newAddonUrl = ""
                    showAddAddon = false
                }) {
                    Text(text = "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAddon = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun InitStatusCard(initState: InitState) {
    val (text, color) = when (initState) {
        is InitState.Loading -> initState.message to MaterialTheme.colorScheme.primaryContainer
        is InitState.Ready -> "${initState.providerCount} providers ready" to MaterialTheme.colorScheme.secondaryContainer
        is InitState.Error -> "Error: ${initState.message}" to MaterialTheme.colorScheme.errorContainer
        else -> "Initializing..." to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = color,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (initState is InitState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Add")
        }
    }
}

@Composable
private fun RepositoryRow(
    repo: RepositoryData,
    plugins: List<SitePlugin>,
    isExpanded: Boolean,
    pluginsDir: File,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onInstall: (SitePlugin, File) -> Unit,
    onUninstall: (File, SitePlugin) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name.ifBlank { "Repository" },
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
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "",
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (plugins.isEmpty()) {
                        Text(
                            text = "Tap refresh to fetch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        plugins.forEach { plugin ->
                            val pluginFile = File(
                                File(pluginsDir, RepositoryManager.getRepoFolderName(repo.url)),
                                RepositoryManager.getPluginFileName(plugin.internalName),
                            )
                            val isInstalled = pluginFile.exists()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Extension,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plugin.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "v${plugin.version}${plugin.language?.let { " - $it" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isInstalled) {
                                    FilledTonalButton(onClick = { onUninstall(pluginFile, plugin) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Remove")
                                    }
                                } else {
                                    Button(onClick = { onInstall(plugin, pluginFile) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Install")
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StremioAddonRow(
    name: String,
    url: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
