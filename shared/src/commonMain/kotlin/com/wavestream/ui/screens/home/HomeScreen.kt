package com.wavestream.ui.screens.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.wavestream.WaveAppInit
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home screen — CloudStream-style "rails" layout:
 *
 *  ┌──────────────────────────────────────────┐
 *  │  HERO BANNER (auto-rotating spotlight)   │
 *  │  Backdrop · title · plot · Play button   │
 *  └──────────────────────────────────────────┘
 *  Section: Trending ──────────────────►
 *  [poster] [poster] [poster] [poster] ...
 *
 *  Section: Popular Movies ────────────►
 *  [poster] [poster] [poster] [poster] ...
 *
 *  Section: Popular Series ────────────►
 *  [poster] [poster] [poster] [poster] ...
 *
 * The first section's items populate the hero banner. When no providers
 * are installed, the screen shows an empty state with a CTA to install
 * extensions — rather than a blank grid.
 */
@Composable
fun HomeScreen(
    onPosterClick: (apiName: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val providers by remember { mutableStateOf(APIHolder.allProviders) }
    var sections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val bootState by WaveAppInit.bootState.collectAsState()

    LaunchedEffect(bootState.stage) {
        if (!bootState.stage.isReady) return@LaunchedEffect
        loading = true
        error = null
        try {
            val results = mutableListOf<HomeSection>()
            val snapshot = providers.withLock { providers.toList() }
            for (api in snapshot) {
                if (!api.hasMainPage) continue
                try {
                    for (mp in api.mainPage) {
                        val request = MainPageRequest(mp.name, mp.data, mp.horizontalImages)
                        val page = withContext(Dispatchers.Default) {
                            api.getMainPage(page = 1, request = request)
                        } ?: continue
                        page.items.forEach { item ->
                            results.add(HomeSection("${api.name} · ${item.name}", item.list))
                        }
                    }
                } catch (_: Throwable) { /* skip failing provider */ }
            }
            sections = results
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load home"
        } finally {
            loading = false
        }
    }

    when {
        loading -> LoadingIndicator(message = "Loading home…")
        error != null -> EmptyState(
            title = "Couldn't load home",
            subtitle = error,
            actionLabel = "Retry",
            onAction = { WaveAppInit.rescan() },
        )
        sections.isEmpty() -> EmptyState(
            title = "No content yet",
            subtitle = "Install extensions from the Extensions tab to populate your home feed. " +
                "Default repos are pre-seeded — try rescanning if you've cleared extensions.",
            actionLabel = "Rescan now",
            onAction = { WaveAppInit.rescan() },
        )
        else -> {
            val hero = sections.firstOrNull()?.items?.firstOrNull()
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                if (hero != null) {
                    item(key = "hero") {
                        HeroBanner(item = hero, onClick = { onPosterClick(hero.apiName, hero.url) })
                    }
                }
                items(sections, key = { it.title }) { section ->
                    HomeSectionRow(section = section, onPosterClick = onPosterClick)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

private data class HomeSection(val title: String, val items: List<SearchResponse>)

@Composable
private fun HeroBanner(item: SearchResponse, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable { onClick() },
    ) {
        // Backdrop image (uses poster URL as fallback — most providers don't
        // supply a separate backdrop URL on search responses).
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Gradient overlay for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )

        // Text content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "From ${item.apiName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) {
                Text("View details")
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        // "Featured" tag in top-left
        Box(
            modifier = Modifier
                .padding(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopStart),
        ) {
            Text(
                text = "FEATURED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    onPosterClick: (apiName: String, url: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${section.items.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(section.items, key = { it.url }) { item ->
                Box(modifier = Modifier.width(108.dp)) {
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
