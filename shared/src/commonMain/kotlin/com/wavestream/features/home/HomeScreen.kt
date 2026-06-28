package com.wavestream.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wavestream.api.APIHolder
import com.wavestream.api.HomePageList
import com.wavestream.api.HomePageResponse
import com.wavestream.api.MainPageRequest
import com.wavestream.api.SearchResponse
import com.wavestream.features.bookmarks.BookmarkRepository
import com.wavestream.features.watchprogress.WatchProgressRepository
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.LoadingGrid
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen — mirrors CloudStream's HomeFragment.
 *
 * Layout:
 *   - Continue Watching rail (from WatchProgressRepository)
 *   - Bookmarks rail (from BookmarkRepository)
 *   - Hero section: random popular item
 *   - Per-provider rails
 */
@Composable
fun HomeScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var heroItem by remember { mutableStateOf<SearchResponse?>(null) }
    val continueWatching by WatchProgressRepository.progress.collectAsState()
    val bookmarks by BookmarkRepository.bookmarks.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            sections = loadHomeContent()
            heroItem = sections.firstOrNull()?.list?.randomOrNull()
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Hero section
        item {
            HeroSection(
                item = heroItem,
                onPlay = { hero -> onNavigateToDetails(hero.apiName, hero.url) },
                onInfo = { hero -> onNavigateToDetails(hero.apiName, hero.url) },
                onSearch = onNavigateToSearch,
            )
        }

        // Continue Watching rail
        val cwList = continueWatching.values.filter { !it.isCompleted && it.positionMs > 10_000 }.sortedByDescending { it.updatedAt }.take(20)
        if (cwList.isNotEmpty()) {
            item {
                Text(
                    "Continue Watching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(cwList) { wp ->
                        PosterCard(
                            item = object : SearchResponse {
                                override val name = wp.title
                                override val url = wp.url
                                override val apiName = wp.apiName
                                override var type: com.wavestream.api.TvType? = null
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

        // Bookmarks rail
        val bmList = bookmarks.values.sortedByDescending { it.addedAt }.take(20)
        if (bmList.isNotEmpty()) {
            item {
                Text(
                    "My Bookmarks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(bmList) { bm ->
                        PosterCard(
                            item = object : SearchResponse {
                                override val name = bm.name
                                override val url = bm.url
                                override val apiName = bm.apiName
                                override var type: com.wavestream.api.TvType? = bm.type
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

        // Loading state
        if (isLoading) {
            item {
                Text("Loading...", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Provider rails
        items(sections) { section ->
            HomeRail(
                title = section.name,
                items = section.list,
                horizontal = section.isHorizontalImages,
                onItemClick = { onNavigateToDetails(it.apiName, it.url) },
            )
        }

        // Empty state when no content
        if (!isLoading && sections.isEmpty() && cwList.isEmpty() && bmList.isEmpty()) {
            item {
                EmptyState(
                    title = "No content available",
                    message = "Install extensions from Settings → Extensions to start watching.",
                )
            }
        }
    }
}

@Composable
private fun HeroSection(
    item: SearchResponse?,
    onPlay: (SearchResponse) -> Unit,
    onInfo: (SearchResponse) -> Unit,
    onSearch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
        if (item?.posterUrl != null) {
            AsyncImage(model = item.posterUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background))))
        }
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            item?.let {
                Text(it.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { onPlay(it) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onInfo(it) }) { Text("Info") }
                }
            } ?: Text("Wavestream", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth(0.6f).clickable(onClick = onSearch), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Search movies, shows...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HomeRail(title: String, items: List<SearchResponse>, horizontal: Boolean, onItemClick: (SearchResponse) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.url + it.apiName }) { item ->
                PosterCard(item = item, onClick = { onItemClick(item) }, horizontal = horizontal)
            }
        }
    }
}

private suspend fun loadHomeContent(): List<HomePageList> = withContext(Dispatchers.Default) {
    val providers = APIHolder.allProviders.toList().filter { it.hasMainPage }
    if (providers.isEmpty()) return@withContext emptyList()
    val sections = mutableListOf<HomePageList>()
    providers.forEach { api ->
        api.mainPage.forEach { mainPageData ->
            try {
                val response = api.getMainPage(1, MainPageRequest(mainPageData.name, mainPageData.data, mainPageData.horizontalImages))
                response?.items?.forEach { list ->
                    sections.add(list.copy(name = "${list.name} • ${api.name}"))
                }
            } catch (e: Throwable) {}
        }
    }
    sections
}
