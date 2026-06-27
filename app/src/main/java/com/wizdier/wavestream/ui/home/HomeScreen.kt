package com.wizdier.wavestream.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.api.SearchResponse
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.ui.components.EmptyState
import com.wizdier.wavestream.ui.components.MovieRow
import com.wizdier.wavestream.ui.components.ShimmerHome
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
    val providers by viewModel.providers.collectAsState()
    val selectedProviderId by viewModel.selectedProviderId.collectAsState()

    val scrollState = rememberLazyListState()
    // Animate the top bar background based on scroll position — transparent
    // at the top (so the hero shows through), fades to solid as you scroll.
    val topBarAlpha by remember {
        derivedStateOf {
            (scrollState.firstVisibleItemIndex * 1f + scrollState.firstVisibleItemScrollOffset / 300f)
                .coerceIn(0f, 1f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Search icon
                    androidx.compose.material3.IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = topBarAlpha),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && homeLists.isEmpty() && continueWatching.isEmpty()) {
                ShimmerHome()
                return@Box
            }

            if (error != null && homeLists.isEmpty()) {
                EmptyState(
                    message = "Couldn't load home: ${error}\n\nAdd a provider in Settings → Providers to start watching."
                )
                return@Box
            }

            if (homeLists.isEmpty() && !isLoading && error == null) {
                EmptyState(
                    message = "No providers installed yet.\n\nAdd a provider repository in Settings → Providers to start watching."
                )
                return@Box
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Continue Watching row (auto-hides if empty)
                if (continueWatching.isNotEmpty()) {
                    item(key = "cw-header") {
                        SectionHeader(stringResource(R.string.home_continue_watching))
                    }
                    item(key = "cw-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(continueWatching, key = { it.rowId }) { entry ->
                                ContinueWatchingCard(entry) {
                                    onOpenDetail(entry.providerId, entry.url)
                                }
                            }
                        }
                    }
                }

                // Provider tabs (if more than one provider installed)
                if (providers.size > 1) {
                    item(key = "provider-tabs") {
                        ProviderTabs(
                            providers = providers,
                            selectedId = selectedProviderId,
                            onSelect = { viewModel.selectProvider(it?.id) }
                        )
                    }
                }

                // Home catalog rows
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ProviderTabs(
    providers: List<com.wizdier.wavestream.data.api.Provider>,
    selectedId: String?,
    onSelect: (com.wizdier.wavestream.data.api.Provider?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" tab
        item(key = "all") {
            ProviderTabChip(
                name = "All",
                isSelected = selectedId == null,
                onClick = { onSelect(null) }
            )
        }
        items(providers, key = { it.id }) { provider ->
            ProviderTabChip(
                name = provider.name,
                isSelected = selectedId == provider.id,
                onClick = { onSelect(provider) }
            )
        }
    }
}

@Composable
private fun ProviderTabChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ContinueWatchingCard(entry: HistoryEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Backdrop image
        val backdrop = entry.backdropUrl ?: entry.posterUrl
        if (!backdrop.isNullOrEmpty()) {
            val request = remember(backdrop) {
                ImageRequest.Builder(context)
                    .data(backdrop)
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

        // Scrim
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

        // Play button overlay (center)
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = "Play",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
        )

        // Title + episode info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
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

        // Progress bar at the bottom
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
