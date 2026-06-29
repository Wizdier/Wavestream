package com.wavestream.ui.screens.library

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
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.PosterCard

@Composable
fun LibraryScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bookmarks", "Watching", "Watched")

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
            0 -> EmptyState(
                title = "No bookmarks yet",
                message = "Bookmark shows and movies to find them here.",
            )
            1 -> EmptyState(
                title = "Not watching anything",
                message = "Start watching a show and it will appear here.",
            )
            2 -> EmptyState(
                title = "Nothing watched yet",
                message = "Completed shows will appear here.",
            )
        }
    }
}
