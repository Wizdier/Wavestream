package com.wizdier.wavestream.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.api.CatalogType
import com.wizdier.wavestream.ui.components.EmptyState
import com.wizdier.wavestream.ui.components.LoadingState
import com.wizdier.wavestream.ui.components.MovieCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenDetail: (providerId: String, url: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val filterOpen by viewModel.filterOpen.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = viewModel::toggleFilterSheet) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.search_filters))
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.setQuery("")
                                viewModel.clearFilters()
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = null)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Active filter chips
        if (filter.types.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filter.types.toList()) { type ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.toggleType(type) },
                        label = { Text(type.displayName) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        when {
            isSearching -> LoadingState(modifier = Modifier.fillMaxWidth().height(200.dp))
            results.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(results, key = { it.url }) { item ->
                        MovieCard(
                            item = item,
                            onClick = { onOpenDetail(item.providerId, item.url) }
                        )
                    }
                }
            }
            query.isEmpty() && recent.isNotEmpty() -> {
                Text(
                    text = stringResource(R.string.search_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recent, key = { it.rowId }) { entry ->
                        AssistChip(
                            onClick = {
                                viewModel.setQuery(entry.query)
                                viewModel.runSearch()
                            },
                            label = { Text(entry.query) },
                            leadingIcon = {
                                IconButton(onClick = { viewModel.removeRecent(entry.query) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
            results.isEmpty() && query.isNotEmpty() -> {
                EmptyState(message = stringResource(R.string.search_empty))
            }
        }
    }

    if (filterOpen) {
        ModalBottomSheet(
            onDismissRequest = viewModel::toggleFilterSheet,
            sheetState = rememberModalBottomSheetState()
        ) {
            FilterSheet(
                filter = filter,
                onToggleType = viewModel::toggleType,
                onClear = viewModel::clearFilters,
                onApply = viewModel::toggleFilterSheet
            )
        }
    }
}

@Composable
private fun FilterSheet(
    filter: com.wizdier.wavestream.data.api.SearchFilter,
    onToggleType: (CatalogType) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.search_filter_type), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogType.entries.filter { it != CatalogType.OTHER }.forEach { type ->
                FilterChip(
                    selected = type in filter.types,
                    onClick = { onToggleType(type) },
                    label = { Text(type.displayName) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear)) }
            TextButton(onClick = onApply) { Text(stringResource(R.string.ok)) }
        }
    }
}
