package com.wavestream.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wavestream.api.APIHolder
import com.wavestream.api.APIRepository
import com.wavestream.api.Resource
import com.wavestream.api.SearchResponse
import com.wavestream.ui.components.PosterCard
import com.wavestream.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Load search history
    val searchHistory = remember { SearchHistoryRepository.load() }

    // Debounced search
    LaunchedEffect(query) {
        searchJob?.cancel()
        if (query.isBlank()) {
            results = emptyList()
            isLoading = false
            hasSearched = false
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(300)
            isLoading = true
            hasSearched = true
            results = performSearch(query)
            isLoading = false
            // Save to history
            if (results.isNotEmpty()) {
                SearchHistoryRepository.add(query, results.size)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                query.isBlank() && searchHistory.isNotEmpty() -> {
                    // Show search history
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            "Recent Searches",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        searchHistory.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = item.query
                                        keyboard?.show()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(item.query, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                if (item.resultCount > 0) {
                                    Text("${item.resultCount} results", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (searchHistory.isNotEmpty()) {
                            TextButton(onClick = { SearchHistoryRepository.clear() }) {
                                Text("Clear History")
                            }
                        }
                    }
                }
                query.isBlank() -> {
                    EmptyState(
                        title = "Search",
                        message = "Start typing to search across all providers.",
                        icon = "Search",
                    )
                }
                hasSearched && results.isEmpty() -> {
                    EmptyState(
                        title = "No results",
                        message = "No results found for \"$query\".\nTry a different search term or install more extensions.",
                        icon = "Empty",
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
