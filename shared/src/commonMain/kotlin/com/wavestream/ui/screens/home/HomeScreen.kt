package com.wavestream.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.wavestream.WaveAppInit
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onNavigateToDetails: (apiName: String, url: String) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var heroItem by remember { mutableStateOf<SearchResponse?>(null) }
    val initState by WaveAppInit.initState.collectAsState()

    // Auto-reload home when init state transitions to Ready
    var lastInitGeneration by remember { mutableStateOf(0) }
    LaunchedEffect(initState) {
        if (initState is com.wavestream.InitState.Ready) {
            val newGen = (initState as com.wavestream.InitState.Ready).providerCount
            if (newGen != lastInitGeneration) {
                lastInitGeneration = newGen
                scope.launch {
                    isLoading = true
                    sections = loadHomeContent()
                    heroItem = sections.firstOrNull()?.list?.randomOrNull()
                    isLoading = false
                }
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        if (sections.isEmpty()) {
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
        item {
            HeroSection(
                item = heroItem,
                onPlay = { hero -> onNavigateToDetails(hero.apiName, hero.url) },
                onInfo = { hero -> onNavigateToDetails(hero.apiName, hero.url) },
            )
        }

        if (isLoading) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    val msg = (initState as? com.wavestream.InitState.Loading)?.message ?: "Loading content..."
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(sections, key = { it.name }) { section ->
            HomeRail(
                title = section.name,
                items = section.list,
                horizontal = section.isHorizontalImages,
                onItemClick = { onNavigateToDetails(it.apiName, it.url) },
            )
        }

        if (!isLoading && sections.isEmpty()) {
            item {
                EmptyState(
                    title = "No content available",
                    message = "Install extensions from Settings → Extensions to start watching.",
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Search")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    item: SearchResponse?,
    onPlay: (SearchResponse) -> Unit,
    onInfo: (SearchResponse) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
        if (item?.posterUrl != null) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            item?.let {
                Text(
                    it.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { onPlay(it) }) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onInfo(it) }) {
                        Text("Info")
                    }
                }
            } ?: Text(
                "Wavestream",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun HomeRail(
    title: String,
    items: List<SearchResponse>,
    horizontal: Boolean,
    onItemClick: (SearchResponse) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items, key = { it.url + it.apiName }) { item ->
                PosterCard(item = item, onClick = { onItemClick(item) }, horizontal = horizontal)
            }
        }
    }
}

private suspend fun loadHomeContent(): List<HomePageList> = withContext(Dispatchers.Default) {
    val providers = APIHolder.allProviders.toList().filter { it.hasMainPage }
    if (providers.isEmpty()) return@withContext emptyList()

    val deferredList = mutableListOf<kotlinx.coroutines.Deferred<List<HomePageList>>>()
    kotlinx.coroutines.coroutineScope {
        for (api in providers) {
            for (mainPageData in api.mainPage) {
                deferredList.add(async {
                    try {
                        val response = api.getMainPage(
                            1,
                            MainPageRequest(mainPageData.name, mainPageData.data, mainPageData.horizontalImages),
                        )
                        response?.items?.map { list ->
                            list.copy(name = "${list.name} • ${api.name}")
                        } ?: emptyList()
                    } catch (e: Throwable) {
                        println("[Home] Failed to load ${api.name}/${mainPageData.name}: ${e.message}")
                        emptyList()
                    }
                })
            }
        }
    }
    deferredList.awaitAll().flatten()
}
