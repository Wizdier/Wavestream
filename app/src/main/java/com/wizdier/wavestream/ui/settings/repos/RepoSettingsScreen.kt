package com.wizdier.wavestream.ui.settings.repos

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.db.entities.RepoEntity
import com.wizdier.wavestream.data.repository.RepoExtension
import com.wizdier.wavestream.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoSettingsScreen(
    onBack: () -> Unit,
    viewModel: RepoSettingsViewModel = koinViewModel()
) {
    val repos by viewModel.repos.collectAsState()
    val extensions by viewModel.extensions.collectAsState()
    val error by viewModel.error.collectAsState()
    val adding by viewModel.adding.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val installing by viewModel.installing.collectAsState()
    var input by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface repository errors as snackbars instead of a stale text banner.
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Add-repo form with example URL hint
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.repos_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !adding
                    )
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.add(input)
                                input = ""
                            }
                        },
                        enabled = !adding && input.isNotBlank()
                    ) {
                        if (adding) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.padding(8.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.repos_add))
                        }
                    }
                }
                Text(
                    text = "Paste any CloudStream-compatible repo.json URL above, or tap a popular repo below to add it:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                // Popular verified CloudStream repos — tap to add.
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExampleRepoChip("Wizdier BDIX", "https://raw.githubusercontent.com/Wizdier/Wizdier-CloudstreamRepo/main/repo.json", viewModel)
                    ExampleRepoChip("CloudStream Official", "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json", viewModel)
                    ExampleRepoChip("CakesTwix (UK)", "https://raw.githubusercontent.com/CakesTwix/cloudstream-extensions-uk/master/repo.json", viewModel)
                    ExampleRepoChip("Phisher", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json", viewModel)
                    ExampleRepoChip("Luna712", "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/master/repo.json", viewModel)
                    ExampleRepoChip("Redowan", "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json", viewModel)
                }
            }

            if (refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            HorizontalDivider()

            if (repos.isEmpty()) {
                EmptyState(
                    message = "No repositories added yet.\n\nAdd a CloudStream repo URL above to install provider extensions.",
                    modifier = Modifier.fillMaxSize()
                )
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(repos, key = { it.rowId }) { repo ->
                    RepoRow(
                        repo = repo,
                        extensions = extensions[repo.rowId].orEmpty(),
                        installing = installing,
                        onRefresh = { viewModel.refresh(repo.rowId) },
                        onRemove = { viewModel.remove(repo.rowId) },
                        onInstall = { ext -> viewModel.install(ext) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: RepoEntity,
    extensions: List<RepoExtension>,
    installing: Set<String>,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onInstall: (RepoExtension) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = repo.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                repo.author?.let {
                    Text(
                        text = "by $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.repos_update))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
            }
        }

        if (extensions.isEmpty()) {
            Text(
                text = "Tap refresh to load available extensions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${extensions.size} extension${if (extensions.size != 1) "s" else ""} available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            extensions.forEach { ext ->
                ExtensionRow(
                    ext = ext,
                    isInstalling = ext.apk in installing,
                    onInstall = { onInstall(ext) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    ext: RepoExtension,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "v${ext.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // File type chip (cs3 or apk)
                Text(
                    text = ".${ext.fileExtension}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                ext.language?.let { lang ->
                    Text(
                        text = lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ext.fileSize != null && ext.fileSize > 0) {
                    Text(
                        text = "${ext.fileSize / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ext.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Show tvTypes if present (CloudStream categorization).
            ext.tvTypes?.takeIf { it.isNotEmpty() }?.let { types ->
                Text(
                    text = "Types: ${types.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ext.authors?.takeIf { it.isNotEmpty() }?.let { authors ->
                Text(
                    text = "by ${authors.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onInstall, enabled = !isInstalling) {
            if (isInstalling) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.repos_install))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExampleRepoChip(
    label: String,
    url: String,
    viewModel: RepoSettingsViewModel
) {
    androidx.compose.material3.AssistChip(
        onClick = { viewModel.add(url) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}
