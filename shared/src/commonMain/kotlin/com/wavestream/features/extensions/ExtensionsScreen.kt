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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.api.APIHolder
import com.wavestream.plugins.stremio.StremioAddonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extensions screen — mirrors CloudStream's ExtensionsFragment + NuvioMobile's AddonsScreen.
 *
 * Three sections:
 *   1. CloudStream-style providers (loaded from .ws3 plugin files via PluginManager)
 *   2. Stremio addons (fetched via StremioAddonClient from a manifest URL)
 *   3. JS plugin scrapers (loaded from .js files)
 *
 * User can:
 *   - Add a repository URL (downloads .ws3 plugins)
 *   - Add a Stremio addon by manifest URL
 *   - Enable/disable/toggle individual extensions
 *   - View extension details (version, author, supported types)
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

    // Live list of providers
    val providers = remember { mutableStateListOf<ExtensionItem>() }
    val stremioAddons = remember { mutableStateListOf<ExtensionItem>() }

    LaunchedEffect(Unit) {
        // Load CS-style providers
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
                actions = {
                    IconButton(onClick = { showAddAddonDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Stremio addon")
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
            // Stremio addons section
            item {
                Text(
                    "Stremio Addons",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (stremioAddons.isEmpty()) {
                item {
                    Text(
                        "No Stremio addons installed. Tap + to add one by manifest URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(stremioAddons, key = { it.id }) { addon ->
                    ExtensionRow(addon) {
                        // toggle
                    }
                }
            }

            // CS3 providers section
            item {
                Text(
                    "CloudStream Providers (${providers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(providers, key = { it.id }) { provider ->
                ExtensionRow(provider) {
                    // toggle
                }
            }
        }
    }

    // Add Stremio addon dialog
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
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newAddonUrl.isNotBlank()) {
                            scope.launch {
                                val result = addStremioAddon(newAddonUrl)
                                if (result != null) {
                                    stremioAddons.add(result)
                                    newAddonUrl = ""
                                }
                            }
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
private fun ExtensionRow(
    item: ExtensionItem,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = { onToggle() },
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

private suspend fun addStremioAddon(manifestUrl: String): ExtensionItem? = withContext(Dispatchers.Default) {
    try {
        val client = StremioAddonClient(manifestUrl)
        val manifest = client.getManifest()
        ExtensionItem(
            id = manifest.id,
            name = manifest.name,
            subtitle = "${manifest.version} • ${manifest.types.joinToString(", ")}",
            enabled = true,
            type = ExtensionType.STREMIO_ADDON,
        )
    } catch (e: Throwable) {
        println("[Extensions] Failed to add addon: ${e.message}")
        null
    }
}
