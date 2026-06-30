package com.wavestream.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.wavestream.InitState
import com.wavestream.WaveAppInit
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onNavigateToDetails: (String, String) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var heroItem by remember { mutableStateOf<SearchResponse?>(null) }
    val initState by WaveAppInit.initState.collectAsState()
    var lastGen by remember { mutableStateOf(0) }

    LaunchedEffect(initState) {
        val ready = initState as? InitState.Ready ?: return@LaunchedEffect
        if (ready.providerCount != lastGen) {
            lastGen = ready.providerCount
            scope.launch {
                isLoading = true
                sections = loadHome()
                heroItem = sections.firstOrNull()?.list?.randomOrNull()
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (sections.isEmpty()) {
            isLoading = true
            sections = loadHome()
            heroItem = sections.firstOrNull()?.list?.randomOrNull()
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Hero(
                item = heroItem,
                onPlay = { onNavigateToDetails(it.apiName, it.url) },
                onInfo = { onNavigateToDetails(it.apiName, it.url) },
            )
        }

        if (isLoading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = (initState as? InitState.Loading)?.message ?: "Loading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(sections, key = { it.name }) { section ->
            Rail(
                title = section.name,
                items = section.list,
                horizontal = section.isHorizontalImages,
                onClick = { onNavigateToDetails(it.apiName, it.url) },
            )
        }

        if (!isLoading && sections.isEmpty()) {
            item {
                EmptyState(
                    title = "No content",
                    message = "Install extensions from Settings.",
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = onNavigateToSearch) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Search")
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(
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
            if (item != null) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Button(onClick = { onPlay(item) }) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Play")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onInfo(item) }) {
                        Text(text = "Info")
                    }
                }
            } else {
                Text(
                    text = "Wavestream",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun Rail(
    title: String,
    items: List<SearchResponse>,
    horizontal: Boolean,
    onClick: (SearchResponse) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
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
                PosterCard(
                    item = item,
                    onClick = { onClick(item) },
                    horizontal = horizontal,
                )
            }
        }
    }
}

private suspend fun loadHome(): List<HomePageList> = withContext(Dispatchers.Default) {
    val providers = APIHolder.allProviders.toList().filter { it.hasMainPage }
    if (providers.isEmpty()) return@withContext emptyList()
    coroutineScope {
        providers
            .flatMap { api ->
                api.mainPage.map { mpd ->
                    async {
                        try {
                            api.getMainPage(1, MainPageRequest(mpd.name, mpd.data, mpd.horizontalImages))
                                ?.items
                                ?.map { it.copy(name = "${it.name} - ${api.name}") }
                                ?: emptyList()
                        } catch (e: Throwable) {
                            emptyList()
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
    }
}
