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
import com.wavestream.core.WaveAppInit
import com.wavestream.plugins.repository.RepositoryManager
import com.wavestream.plugins.stremio.StremioAddonRepository
import com.wavestream.plugins.stremio.StoredStremioAddon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class StoredRepo(val url: String, val name: String = "")
private val repoSerializer = StoredRepo.serializer()
private val repoListSerializer = ListSerializer(repoSerializer)
private const val REPOS_KEY = "cs_repositories_v3"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showAddAddonDialog by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var newAddonUrl by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val repos = remember { mutableStateListOf<StoredRepo>() }
    val stremioAddons by StremioAddonRepository.addons.collectAsState()
    val providers = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        repos.clear()
        repos.addAll(DataStore.getSerializedList(REPOS_KEY, repoSerializer) ?: emptyList())
        providers.clear()
        providers.addAll(APIHolder.allProviders.toList().map { "${it.name} - ${it.mainUrl}" })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (statusMsg != null) {
                item { Text(statusMsg!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 4.dp)) }
            }

            // Repositories
            item { SectionHeader("Repositories (${repos.size})", "Add Repo") { showAddRepoDialog = true } }
            if (repos.isEmpty()) {
                item { EmptyHint("No repositories added. Add a CloudStream repository URL to download plugins.") }
            } else {
                items(repos, key = { it.url }) { repo ->
                    RepoRow(repo, onDelete = {
                        repos.remove(repo)
                        DataStore.setSerializedList(REPOS_KEY, repos.toList(), repoSerializer)
                    }, onRefresh = {
                        scope.launch {
                            statusMsg = "Fetching ${repo.url}..."
                            val plugins = withContext(Dispatchers.Default) { runCatching { RepositoryManager.getRepoPlugins(repo.url) }.getOrNull() }
                            statusMsg = if (plugins != null) "Found ${plugins.size} plugins. They will be downloaded on next app restart."
                                else "Failed to fetch. Check URL."
                        }
                    })
                }
            }

            // Stremio Addons
            item { SectionHeader("Stremio Addons (${stremioAddons.size})", "Add Addon") { showAddAddonDialog = true } }
            if (stremioAddons.isEmpty()) {
                item { EmptyHint("No Stremio addons. Add one by manifest URL to start streaming.") }
            } else {
                items(stremioAddons, key = { it.manifestUrl }) { addon ->
                    AddonRow(addon,
                        onToggle = { StremioAddonRepository.toggleAddon(addon.manifestUrl) },
                        onDelete = { StremioAddonRepository.removeAddon(addon.manifestUrl) },
                    )
                }
            }

            // Active Providers
            item { SectionHeader("Active Providers (${providers.size})", null, null) }
            if (providers.isEmpty()) {
                item { EmptyHint("No providers loaded. Add a repository or Stremio addon to get started.") }
            } else {
                items(providers, key = { it }) { provider ->
                    Text(provider, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Repository") },
            text = { OutlinedTextField(value = newRepoUrl, onValueChange = { newRepoUrl = it }, label = { Text("Repository URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = {
                val url = newRepoUrl.trim()
                if (url.isNotBlank() && repos.none { it.url == url }) {
                    repos.add(StoredRepo(url))
                    DataStore.setSerializedList(REPOS_KEY, repos.toList(), repoSerializer)
                    statusMsg = "Repository saved. Plugins will download on restart."
                }
                newRepoUrl = ""; showAddRepoDialog = false
            }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddRepoDialog = false }) { Text("Cancel") } },
        )
    }

    if (showAddAddonDialog) {
        AlertDialog(
            onDismissRequest = { showAddAddonDialog = false },
            title = { Text("Add Stremio Addon") },
            text = { OutlinedTextField(value = newAddonUrl, onValueChange = { newAddonUrl = it }, label = { Text("Manifest URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = {
                val url = newAddonUrl.trim()
                if (url.isNotBlank()) {
                    scope.launch {
                        statusMsg = "Adding addon..."
                        val success = withContext(Dispatchers.Default) { StremioAddonRepository.addAddon(url) }
                        statusMsg = if (success) "Addon added! Check Home tab for content." else "Failed to add addon."
                    }
                }
                newAddonUrl = ""; showAddAddonDialog = false
            }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddAddonDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String, buttonText: String?, onButtonClick: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (buttonText != null && onButtonClick != null) {
            TextButton(onClick = onButtonClick) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(buttonText) }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp))
}

@Composable
private fun RepoRow(repo: StoredRepo, onDelete: () -> Unit, onRefresh: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.name.ifBlank { "Repository" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Extension, "Refresh") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddonRow(addon: StoredStremioAddon, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(addon.name.ifBlank { "Stremio Addon" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(addon.manifestUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Switch(checked = addon.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
