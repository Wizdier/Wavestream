package com.wizdier.wavestream.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.api.SearchResponse
import com.wizdier.wavestream.data.db.entities.FavoriteEntity
import com.wizdier.wavestream.ui.components.EmptyState
import com.wizdier.wavestream.ui.components.MovieCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenDetail: (providerId: String, url: String) -> Unit,
    viewModel: FavoritesViewModel = koinViewModel()
) {
    val favorites by viewModel.favorites.collectAsState()
    val listNames by viewModel.listNames.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.favorites_title)) }) }
    ) { padding ->
        if (favorites.isEmpty()) {
            EmptyState(message = stringResource(R.string.favorites_empty), modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(listNames, key = { it }) { listName ->
                val items = favorites.filter { it.listName == listName }
                Text(
                    text = listName,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.rowId }) { fav ->
                        val response = SearchResponse(
                            id = fav.itemId,
                            name = fav.title,
                            url = fav.url,
                            posterUrl = fav.posterUrl,
                            backdropUrl = fav.backdropUrl,
                            providerId = fav.providerId,
                            providerName = fav.providerId
                        )
                        MovieCard(item = response, onClick = { onOpenDetail(fav.providerId, fav.url) })
                    }
                }
            }
        }
    }
}
