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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    var input by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
            // Add repo form
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.repos_add))
                }
            }
            HorizontalDivider()

            if (repos.isEmpty()) {
                EmptyState(message = stringResource(R.string.repos_empty))
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
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onInstall: (RepoExtension) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = repo.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                repo.author?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.repos_update))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        if (extensions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            extensions.forEach { ext ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "• ${ext.name} v${ext.version}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ext.description?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(onClick = { onInstall(ext) }) {
                        Text(stringResource(R.string.repos_install))
                    }
                }
            }
        }
    }
}
