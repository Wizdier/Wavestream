package com.wavestream.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.PosterCard
import com.wavestream.ui.library.LibraryEntry
import com.wavestream.ui.library.LocalLibraryStore

/**
 * Library screen. Shows the user's saved items (favorites / watchlist).
 * Backed by [LocalLibraryStore] which on Android uses SharedPreferences
 * and on desktop uses a JSON file.
 *
 * The library is intentionally minimal — just a list of saved search
 * responses. A full implementation would subdivide by status (Watching /
 * PlanToWatch / Completed / Dropped) but that's beyond the scope of the
 * reproduction guide.
 */
@Composable
fun LibraryScreen(
    onPosterClick: (apiName: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = LocalLibraryStore.current
    var items by remember { mutableStateOf(store.load()) }

    if (items.isEmpty()) {
        EmptyState(
            title = "Your library is empty",
            subtitle = "Long-press a poster on the details screen to add it to your library.",
            modifier = modifier,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items) { entry ->
            PosterCard(
                title = entry.name,
                posterUrl = entry.posterUrl,
                onClick = { onPosterClick(entry.apiName, entry.url) },
            )
        }
    }
}
