package com.wavestream.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wavestream.ui.components.EmptyState

@Composable
fun LibraryScreen(onNavigateToDetails: (String, String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Bookmarks", "Watching", "Watched").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(text = label) },
                )
            }
        }
        when (tab) {
            0 -> EmptyState(
                title = "No bookmarks",
                message = "Bookmark shows to find them here.",
            )
            1 -> EmptyState(
                title = "Not watching",
                message = "Start watching something.",
            )
            2 -> EmptyState(
                title = "Nothing watched",
                message = "Completed shows appear here.",
            )
        }
    }
}
