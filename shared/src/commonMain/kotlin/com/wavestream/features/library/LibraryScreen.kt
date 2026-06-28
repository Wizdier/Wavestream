package com.wavestream.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.api.SearchResponse
import com.wavestream.ui.components.PosterCard
import com.wavestream.ui.components.LoadingPosterCard

/**
 * Library screen — mirrors CloudStream's LibraryFragment.
 *
 * Tabs:
 *   - Bookmarks: items the user has saved
 *   - Watched: items the user has marked as watched
 *   - Watching: items currently being watched (with progress)
 *
 * All data is persisted locally via DataStore + optionally synced to MAL/AniList/Trakt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bookmarks", "Watching", "Watched")

    // TODO: load from DataStore
    val items = remember { mutableStateListOf<SearchResponse>() }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        LazyVerticalGrid(columns = GridCells.Adaptive(120.dp)) {
                            items(8) { LoadingPosterCard() }
                        }
                    }
                    items.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize().align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Nothing here yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Bookmark shows and they'll appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items, key = { it.url + it.apiName }) { item ->
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
