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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private const val REPOS_KEY = "cs_repositories_v2"
private const val STREMIO_ADDONS_KEY = "stremio_addons_v2"

@Serializable
private data class StoredRepo(val url: String, val name: String = "")

@Serializable
private data class StoredAddon(val manifestUrl: String, val name: String = "", val enabled: Boolean = true)

private val repoSerializer = StoredRepo.serializer()
private val repoListSerializer = ListSerializer(repoSerializer)
private val addonSerializer = StoredAddon.serializer()
private val addonListSerializer = ListSerializer(addonSerializer)

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
    var isLoading by remember { mutableStateOf(true) }

    val repos = remember { mutableStateListOf<StoredRepo>() }
    val stremioAddons = remember { mutableStateListOf<StoredAddon>() }
    val providers = remember { mutableStateListOf<ExtensionItem>() }

    LaunchedEffect(Unit) {
        isLoading = true
        // Load repos using proper serialization
        val storedRepos = DataStore.getSerializedList(REPOS_KEY, repoSerializer) ?: emptyList()
        repos.clear()
        repos.addAll(storedRepos)

        // Load addons using proper serialization
        val storedAddons = DataStore.getSerializedList(STREMIO_ADDONS_KEY, addonSerializer) ?: emptyList()
        stremioAddons.clear()
        stremioAddons.addAll(storedAddons)

        // Load providers
        providers.clear()
        APIHolder.allProviders.toList().forEach { api ->
            providers.add(ExtensionItem(
                id = api.name,
                name = api.name,
                subtitle = "${api.mainUrl} - ${api.lang}",
                enabled = true,
            ))
        }
        isLoading = false
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

            // Repositories
            item {
                SectionHeader("Repositories (${repos.size})", "Add Repo") { showAddRepoDialog = true }
            }
            if (repos.isEmpty()) {
                item { EmptyHint("No repositories added. Tap 'Add Repo' to add a CloudStream repository URL.") }
            } else {
                items(repos, key = { it.url }) { repo ->
                    RepoRow(
                        repo = repo,
                        onDelete = {
                            repos.remove(repo)
                            DataStore.setSerializedList(REPOS_KEY, repos.toList(), repoSerializer)
                        },
                        onRefresh = {
                            scope.launch {
                                statusMessage = "Fetching ${repo.url}..."
                                val plugins = withContext(Dispatchers.Default) {
                                    runCatching { RepositoryManager.getRepoPlugins(repo.url) }.getOrNull()
                                }
                                statusMessage = if (plugins != null) {
                                    "Found ${plugins.size} plugins in ${repo.url}"
                                } else {
                                    "Failed to fetch: ${repo.url}"
                                }
                            }
                        },
                    )
                }
            }

            // Stremio Addons
            item {
                SectionHeader("Stremio Addons (${stremioAddons.size})", "Add Addon") { showAddAddonDialog = true }
            }
            if (stremioAddons.isEmpty()) {
                item { EmptyHint("No Stremio addons installed. Tap 'Add Addon' to add one by manifest URL.") }
            } else {
                items(stremioAddons, key = { it.manifestUrl }) { addon ->
                    AddonRow(
                        addon = addon,
                        onToggle = {
                            val idx = stremioAddons.indexOf(addon)
                            if (idx >= 0) {
                                stremioAddons[idx] = addon.copy(enabled = !addon.enabled)
                                DataStore.setSerializedList(STREMIO_ADDONS_KEY, stremioAddons.toList(), addonSerializer)
                            }
                        },
                        onDelete = {
                            stremioAddons.remove(addon)
                            DataStore.setSerializedList(STREMIO_ADDONS_KEY, stremioAddons.toList(), addonSerializer)
                        },
                    )
                }
            }

            // Providers
            item { SectionHeader("CloudStream Providers (${providers.size})", null, null) }
            if (providers.isEmpty()) {
                item { EmptyHint("No providers loaded. Add a repository above to install providers.") }
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

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Repository") },
            text = {
                OutlinedTextField(
                    value = newRepoUrl,
                    onValueChange = { newRepoUrl = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://example.com/repo.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = newRepoUrl.trim()
                    if (url.isNotBlank() && repos.none { it.url == url }) {
                        repos.add(StoredRepo(url))
                        DataStore.setSerializedList(REPOS_KEY, repos.toList(), repoSerializer)
                        scope.launch {
                            statusMessage = "Fetching: $url"
                            val plugins = withContext(Dispatchers.Default) {
                                runCatching { RepositoryManager.getRepoPlugins(url) }.getOrNull()
                            }
                            statusMessage = if (plugins != null) "Found ${plugins.size} plugins."
                                else "Failed to fetch. Check URL."
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
                OutlinedTextField(
                    value = newAddonUrl,
                    onValueChange = { newAddonUrl = it },
                    label = { Text("Manifest URL") },
                    placeholder = { Text("https://example.com/manifest.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = newAddonUrl.trim()
                    if (url.isNotBlank() && stremioAddons.none { it.manifestUrl == url }) {
                        stremioAddons.add(StoredAddon(manifestUrl = url, name = url, enabled = true))
                        DataStore.setSerializedList(STREMIO_ADDONS_KEY, stremioAddons.toList(), addonSerializer)
                        scope.launch {
                            statusMessage = "Fetching: $url"
                            val name = withContext(Dispatchers.Default) {
                                runCatching {
                                    StremioAddonClient(url).getManifest().name
                                }.getOrElse { url }
                            }
                            val idx = stremioAddons.indexOfFirst { it.manifestUrl == url }
                            if (idx >= 0) {
                                stremioAddons[idx] = stremioAddons[idx].copy(name = name)
                                DataStore.setSerializedList(STREMIO_ADDONS_KEY, stremioAddons.toList(), addonSerializer)
                            }
                            statusMessage = "Added: $name"
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

@Composable
private fun SectionHeader(title: String, buttonText: String?, onButtonClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (buttonText != null && onButtonClick != null) {
            TextButton(onClick = onButtonClick) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RepoRow(repo: StoredRepo, onDelete: () -> Unit, onRefresh: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.name.ifBlank { "Repository" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Extension, "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddonRow(addon: StoredAddon, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(addon.name.ifBlank { "Stremio Addon" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(addon.manifestUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Switch(checked = addon.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ProviderRow(provider: ExtensionItem, onToggle: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(provider.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = provider.enabled, onCheckedChange = { onToggle(it) })
        }
    }
}

private data class ExtensionItem(val id: String, val name: String, val subtitle: String, val enabled: Boolean)
