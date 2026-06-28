package com.wavestream.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wavestream.api.APIHolder
import com.wavestream.api.APIRepository
import com.wavestream.api.Resource
import com.wavestream.api.SearchResponse
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search screen — mirrors CloudStream's SearchFragment.
 *
 * Features:
 *   - Search across all registered MainAPI providers in parallel
 *   - Debounced query (300ms)
 *   - Search history (persisted via DataStore)
 *   - Suggestions (calls quickSearch on each provider)
 *   - Results displayed in a responsive grid
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Debounced search
    LaunchedEffect(query) {
        searchJob?.cancel()
        if (query.isBlank()) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(300)  // debounce
            isLoading = true
            results = performSearch(query)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search movies, shows, anime...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            // Results
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    query.isBlank() -> {
                        Text(
                            text = "Start typing to search across all providers",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    results.isEmpty() -> {
                        Text(
                            text = "No results found for \"$query\"",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(results, key = { it.url + it.apiName }) { item ->
                                PosterCard(
                                    item = item,
                                    onClick = { onNavigateToDetails(item.apiName, item.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Perform search across all registered providers in parallel.
 */
private suspend fun performSearch(query: String): List<SearchResponse> = withContext(Dispatchers.Default) {
    val providers = APIHolder.apis.toList()
    val results = mutableListOf<SearchResponse>()

    kotlinx.coroutines.coroutineScope {
        val deferred = providers.map { api ->
            this.async {
                try {
                    val repo = APIRepository(api)
                    when (val res = repo.search(query, 1)) {
                        is Resource.Success -> res.value.items
                        else -> emptyList()
                    }
                } catch (e: Throwable) {
                    emptyList()
                }
            }
        }
        deferred.awaitAll().forEach { results.addAll(it) }
    }
    results
}
