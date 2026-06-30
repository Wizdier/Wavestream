package com.wavestream.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search screen. Fans the query out across every loaded provider in
 * parallel and merges results into a single grid, deduplicated by URL.
 *
 * The query is debounced (300ms) to avoid hammering providers while the
 * user is still typing.
 */
@Composable
fun SearchScreen(
    onPosterClick: (apiName: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Debounced search
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            loading = false
            hasSearched = false
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        error = null
        scope.launch {
            try {
                val providers = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
                val merged = mutableListOf<SearchResponse>()
                val jobs = providers.map { api ->
                    launch(Dispatchers.Default) {
                        try {
                            val r = api.search(query)
                            if (r != null) {
                                synchronized(merged) { merged.addAll(r) }
                            }
                        } catch (_: Throwable) { /* skip */ }
                    }
                }
                jobs.forEach { it.join() }
                // De-duplicate by URL, preferring the first occurrence.
                val deduped = merged.distinctBy { it.url }
                results = deduped
                hasSearched = true
            } catch (e: Throwable) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies, series, anime…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        Spacer(Modifier.height(12.dp))

        when {
            loading -> LoadingIndicator(message = "Searching…")
            error != null -> EmptyState(
                title = "Search failed",
                subtitle = error,
                actionLabel = "Retry",
                onAction = { /* re-trigger by typing */ },
            )
            query.isBlank() -> EmptyState(
                title = "Find something to watch",
                subtitle = "Type a title above to search across all installed providers.",
            )
            results.isEmpty() && hasSearched -> EmptyState(
                title = "No results",
                subtitle = "Try a different query or install more extensions.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(results) { item ->
                    PosterCard(
                        title = item.name,
                        posterUrl = item.posterUrl,
                        onClick = { onPosterClick(item.apiName, item.url) },
                    )
                }
            }
        }
    }
}
