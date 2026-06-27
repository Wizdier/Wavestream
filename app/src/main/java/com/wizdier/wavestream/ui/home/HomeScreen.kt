package com.wizdier.wavestream.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.ui.components.DelayedLoading
import com.wizdier.wavestream.ui.components.EmptyState
import com.wizdier.wavestream.ui.components.MovieRow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (providerId: String, url: String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val homeLists by viewModel.homeLists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.Bold
                )
            })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Continue Watching
                if (continueWatching.isNotEmpty()) {
                    item(key = "cw-header") {
                        Text(
                            text = stringResource(R.string.home_continue_watching),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    item(key = "cw-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(continueWatching, key = { it.rowId }) { entry ->
                                ContinueWatchingCard(entry = entry, onClick = {
                                    onOpenDetail(entry.providerId, entry.url)
                                })
                            }
                        }
                    }
                }

                // Loading state — only fades in after a short delay (no flicker)
                if (isLoading && homeLists.isEmpty() && continueWatching.isEmpty()) {
                    item(key = "loading") {
                        DelayedLoading(isLoading = true, modifier = Modifier.fillMaxWidth())
                    }
                }

                // Error state — friendly retry
                if (error != null && homeLists.isEmpty()) {
                    item(key = "error") {
                        EmptyState(
                            message = "Couldn't load home: ${error}\n\nMake sure you have providers installed in Settings → Providers.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                }

                // Empty state — encourage user to add providers
                if (homeLists.isEmpty() && !isLoading && error == null) {
                    item(key = "empty") {
                        EmptyState(
                            message = "No providers installed yet.\n\nAdd a provider repository in Settings → Providers to start watching.",
                            icon = { Icon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.width(56.dp).height(56.dp)
                            )},
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                    item(key = "open-search-cta") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Button(onClick = onOpenSearch) {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.nav_search))
                            }
                        }
                    }
                }

                // Home catalog rows — one per provider's HomePageList
                items(homeLists, key = { it.name }) { list ->
                    MovieRow(
                        title = list.name,
                        items = list.items,
                        onClick = { onOpenDetail(it.providerId, it.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(entry: HistoryEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .width(200.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (!entry.backdropUrl.isNullOrEmpty()) {
            val request = remember(entry.backdropUrl) {
                ImageRequest.Builder(context)
                    .data(entry.backdropUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        // Scrim for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            val pct = if (entry.durationMs > 0) (entry.progressMs * 100 / entry.durationMs).toInt() else 0
            Text(
                text = "S${entry.season} E${entry.episode} · $pct% watched",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        // Progress bar at the bottom of the card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            val pct = if (entry.durationMs > 0)
                (entry.progressMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
            else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
