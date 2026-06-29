package com.wavestream.features.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.api.APIHolder
import com.wavestream.core.storage.DataStore
import com.wavestream.plugins.repository.RepositoryManager
import com.wavestream.plugins.stremio.StremioAddonClient
import com.wavestream.plugins.stremio.ManagedAddon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private const val REPOS_KEY = "cs_repositories"
private const val STREMIO_ADDONS_KEY = "stremio_addons"

@Serializable
private data class StoredRepo(val url: String, val name: String = "")

@Serializable
private data class StoredAddon(val manifestUrl: String, val name: String = "", val enabled: Boolean = true)

/**
 * Extensions screen — manage CloudStream repositories, plugins, and Stremio addons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showAddAddonDialog by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var newAddonUrl by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Load stored repos and addons
    val repos = remember { mutableStateListOf<StoredRepo>() }
    val stremioAddons = remember { mutableStateListOf<StoredAddon>() }
    val providers = remember { mutableStateListOf<ExtensionItem>() }

    LaunchedEffect(Unit) {
        // Load stored repos
        @Suppress("UNCHECKED_CAST")
        val storedRepos = DataStore.getKey(REPOS_KEY, List::class.java) as? List<StoredRepo> ?: emptyList()
        repos.clear()
        repos.addAll(storedRepos)

        // Load stored Stremio addons
        @Suppress("UNCHECKED_CAST")
        val storedAddons = DataStore.getKey(STREMIO_ADDONS_KEY, List::class.java) as? List<StoredAddon> ?: emptyList()
        stremioAddons.clear()
        stremioAddons.addAll(storedAddons)

        // Load CS providers
        providers.clear()
        APIHolder.allProviders.toList().forEach { api ->
            providers.add(ExtensionItem(
                id = api.name,
                name = api.name,
                subtitle = "${api.mainUrl} • ${api.lang}",
                enabled = true,
                type = ExtensionType.CS3_PROVIDER,
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Status message
            if (statusMessage != null) {
                item {
                    Text(
                        statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Repositories section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Repositories (${repos.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { showAddRepoDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Repo")
                    }
                }
            }
            if (repos.isEmpty()) {
                item {
                    Text(
                        "No repositories added. Tap 'Add Repo' to add a CloudStream repository URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(repos, key = { it.url }) { repo ->
                    RepoRow(
                        repo = repo,
                        onDelete = {
                            repos.remove(repo)
                            saveRepos(repos)
                        },
                        onRefresh = {
                            scope.launch {
                                statusMessage = "Refreshing ${repo.url}..."
                                val plugins = withContext(Dispatchers.Default) {
                                    RepositoryManager.getRepoPlugins(repo.url)
                                }
                                if (plugins != null) {
                                    statusMessage = "Found ${plugins.size} plugins in ${repo.url}"
                                } else {
                                    statusMessage = "Failed to fetch repository: ${repo.url}"
                                }
                            }
                        },
                    )
                }
            }

            // Stremio Addons section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Stremio Addons (${stremioAddons.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { showAddAddonDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Addon")
                    }
                }
            }
            if (stremioAddons.isEmpty()) {
                item {
                    Text(
                        "No Stremio addons installed. Tap 'Add Addon' to add one by manifest URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(stremioAddons, key = { it.manifestUrl }) { addon ->
                    AddonRow(
                        addon = addon,
                        onToggle = {
                            val idx = stremioAddons.indexOf(addon)
                            if (idx >= 0) {
                                stremioAddons[idx] = addon.copy(enabled = !addon.enabled)
                                saveAddons(stremioAddons)
                            }
                        },
                        onDelete = {
                            stremioAddons.remove(addon)
                            saveAddons(stremioAddons)
                        },
                    )
                }
            }

            // CS3 Providers section
            item {
                Text(
                    "CloudStream Providers (${providers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (providers.isEmpty()) {
                item {
                    Text(
                        "No providers loaded. Add a repository above to install providers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(providers, key = { it.id }) { provider ->
                    ProviderRow(provider) { newValue ->
                        val idx = providers.indexOfFirst { it.id == provider.id }
                        if (idx >= 0) {
                            providers[idx] = provider.copy(enabled = newValue)
                            DataStore.setKey("provider_enabled_${provider.id}", newValue)
                        }
                    }
                }
            }
        }
    }

    // Add Repository dialog
    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Repository") },
            text = {
                Column {
                    Text(
                        "Enter a CloudStream repository JSON URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = { newRepoUrl = it },
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://example.com/repo.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newRepoUrl.isNotBlank()) {
                            val url = newRepoUrl.trim()
                            if (repos.none { it.url == url }) {
                                repos.add(StoredRepo(url))
                                saveRepos(repos)
                                scope.launch {
                                    statusMessage = "Fetching repository: $url"
                                    val plugins = withContext(Dispatchers.Default) {
                                        RepositoryManager.getRepoPlugins(url)
                                    }
                                    statusMessage = if (plugins != null) {
                                        "Repository added. Found ${plugins.size} plugins."
                                    } else {
                                        "Repository added but failed to fetch plugins. Check the URL."
                                    }
                                }
                            }
                            newRepoUrl = ""
                        }
                        showAddRepoDialog = false
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Add Stremio Addon dialog
    if (showAddAddonDialog) {
        AlertDialog(
            onDismissRequest = { showAddAddonDialog = false },
            title = { Text("Add Stremio Addon") },
            text = {
                Column {
                    Text(
                        "Enter a Stremio addon manifest URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAddonUrl,
                        onValueChange = { newAddonUrl = it },
                        label = { Text("Manifest URL") },
                        placeholder = { Text("https://example.com/manifest.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newAddonUrl.isNotBlank()) {
                            val url = newAddonUrl.trim()
                            if (stremioAddons.none { it.manifestUrl == url }) {
                                stremioAddons.add(StoredAddon(manifestUrl = url, name = url, enabled = true))
                                saveAddons(stremioAddons)
                                scope.launch {
                                    statusMessage = "Fetching addon manifest: $url"
                                    val name = withContext(Dispatchers.Default) {
                                        try {
                                            val client = StremioAddonClient(url)
                                            val manifest = client.getManifest()
                                            manifest.name
                                        } catch (e: Throwable) {
                                            url
                                        }
                                    }
                                    val idx = stremioAddons.indexOfFirst { it.manifestUrl == url }
                                    if (idx >= 0) {
                                        stremioAddons[idx] = stremioAddons[idx].copy(name = name)
                                        saveAddons(stremioAddons)
                                    }
                                    statusMessage = "Addon added: $name"
                                }
                            }
                            newAddonUrl = ""
                        }
                        showAddAddonDialog = false
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAddonDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RepoRow(repo: StoredRepo, onDelete: () -> Unit, onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name.ifBlank { "Repository" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = repo.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Extension, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddonRow(addon: StoredAddon, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = addon.name.ifBlank { "Stremio Addon" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = addon.manifestUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(checked = addon.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: ExtensionItem, onToggle: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(provider.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = provider.enabled,
                onCheckedChange = { newValue -> onToggle(newValue) },
            )
        }
    }
}

private data class ExtensionItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val enabled: Boolean,
    val type: ExtensionType,
)

private enum class ExtensionType { CS3_PROVIDER, STREMIO_ADDON, JS_PLUGIN }

private fun saveRepos(repos: List<StoredRepo>) {
    DataStore.setKey(REPOS_KEY, repos)
}

private fun saveAddons(addons: List<StoredAddon>) {
    DataStore.setKey(STREMIO_ADDONS_KEY, addons)
}
