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
import com.wavestream.api.TvType
import com.wavestream.features.bookmarks.BookmarkRepository
import com.wavestream.features.watchprogress.WatchProgressRepository
import com.wavestream.ui.components.PosterCard
import com.wavestream.ui.components.EmptyState

/**
 * Library screen — mirrors CloudStream's LibraryFragment.
 * This is a main tab (accessible via bottom navigation).
 */
@Composable
fun LibraryScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bookmarks", "Watching", "Watched")

    // Load data from repositories
    val bookmarks by BookmarkRepository.bookmarks.collectAsState()
    val watchProgress by WatchProgressRepository.progress.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Bookmarks
                val bookmarkList = bookmarks.values.sortedByDescending { it.addedAt }
                if (bookmarkList.isEmpty()) {
                    EmptyState(
                        title = "No bookmarks yet",
                        message = "Bookmark shows and movies to find them here.",
                        icon = "🔖",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(bookmarkList, key = { it.id }) { bm ->
                            PosterCard(
                                item = object : SearchResponse {
                                    override val name = bm.name
                                    override val url = bm.url
                                    override val apiName = bm.apiName
                                    override var type: TvType? = runCatching { TvType.valueOf(bm.typeName) }.getOrNull()
                                    override var posterUrl: String? = bm.posterUrl
                                    override var posterHeaders: Map<String, String>? = null
                                    override var id: Int? = null
                                    override var quality: com.wavestream.api.SearchQuality? = null
                                },
                                onClick = { onNavigateToDetails(bm.apiName, bm.url) },
                            )
                        }
                    }
                }
            }
            1 -> {
                // Watching (in-progress)
                val watchingList = watchProgress.values
                    .filter { !it.isCompleted && it.positionMs > 10_000 }
                    .sortedByDescending { it.updatedAt }
                if (watchingList.isEmpty()) {
                    EmptyState(
                        title = "Not watching anything",
                        message = "Start watching a show and it will appear here.",
                        icon = "▶️",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(watchingList, key = { it.id }) { wp ->
                            PosterCard(
                                item = object : SearchResponse {
                                    override val name = wp.title
                                    override val url = wp.url
                                    override val apiName = wp.apiName
                                    override var type: TvType? = null
                                    override var posterUrl: String? = wp.posterUrl
                                    override var posterHeaders: Map<String, String>? = null
                                    override var id: Int? = null
                                    override var quality: com.wavestream.api.SearchQuality? = null
                                },
                                onClick = { onNavigateToDetails(wp.apiName, wp.url) },
                            )
                        }
                    }
                }
            }
            2 -> {
                // Watched (completed)
                val watchedList = watchProgress.values
                    .filter { it.isCompleted }
                    .sortedByDescending { it.updatedAt }
                if (watchedList.isEmpty()) {
                    EmptyState(
                        title = "Nothing watched yet",
                        message = "Completed shows and movies will appear here.",
                        icon = "✅",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(watchedList, key = { it.id }) { wp ->
                            PosterCard(
                                item = object : SearchResponse {
                                    override val name = wp.title
                                    override val url = wp.url
                                    override val apiName = wp.apiName
                                    override var type: TvType? = null
                                    override var posterUrl: String? = wp.posterUrl
                                    override var posterHeaders: Map<String, String>? = null
                                    override var id: Int? = null
                                    override var quality: com.wavestream.api.SearchQuality? = null
                                },
                                onClick = { onNavigateToDetails(wp.apiName, wp.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}
