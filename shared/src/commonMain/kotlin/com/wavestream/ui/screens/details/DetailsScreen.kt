package com.wavestream.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.SubtitleFile
import com.wavestream.ui.components.ErrorState
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    apiName: String,
    url: String,
    onNavigateToPlayer: (url: String, source: String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (apiName: String, url: String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    var loadResponse by remember { mutableStateOf<LoadResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoadingLinks by remember { mutableStateOf(false) }
    var availableLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var showLinkPicker by remember { mutableStateOf(false) }

    LaunchedEffect(apiName, url) {
        isLoading = true
        error = null
        try {
            val api = APIHolder.getApiFromNameNull(apiName)
            if (api == null) {
                error = "Provider '$apiName' not found. Make sure the extension is installed and enabled."
            } else {
                val response = api.load(url)
                if (response == null) {
                    error = "Failed to load content."
                } else {
                    loadResponse = response
                }
            }
        } catch (e: Throwable) {
            error = e.message ?: "Unknown error"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loadResponse?.name ?: "Loading...", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingIndicator()
                error != null -> ErrorState(
                    message = error!!,
                    onRetry = { onNavigateBack() },
                )
                loadResponse != null -> {
                    val resp = loadResponse!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        item { DetailsHero(resp) }

                        item {
                            DetailsActions(
                                response = resp,
                                isLoadingLinks = isLoadingLinks,
                                onPlay = {
                                    scope.launch {
                                        if (resp.type.isMovieType()) {
                                            isLoadingLinks = true
                                            val links = loadLinksForItem(apiName, resp, null)
                                            isLoadingLinks = false
                                            if (links.isNotEmpty()) {
                                                val best = links.maxByOrNull { it.quality } ?: links.first()
                                                onNavigateToPlayer(best.url, best.source)
                                            }
                                        } else {
                                            val firstEp = when (resp) {
                                                is TvSeriesLoadResponse -> resp.episodes.firstOrNull()
                                                is AnimeLoadResponse -> resp.episodes.values.firstOrNull()?.firstOrNull()
                                                else -> null
                                            }
                                            if (firstEp != null) {
                                                isLoadingLinks = true
                                                val links = loadLinksForItem(apiName, resp, firstEp)
                                                isLoadingLinks = false
                                                if (links.isNotEmpty()) {
                                                    val best = links.maxByOrNull { it.quality } ?: links.first()
                                                    onNavigateToPlayer(best.url, best.source)
                                                }
                                            }
                                        }
                                    }
                                },
                                onShowSources = {
                                    scope.launch {
                                        isLoadingLinks = true
                                        val links = if (resp.type.isMovieType()) {
                                            loadLinksForItem(apiName, resp, null)
                                        } else {
                                            val firstEp = when (resp) {
                                                is TvSeriesLoadResponse -> resp.episodes.firstOrNull()
                                                is AnimeLoadResponse -> resp.episodes.values.firstOrNull()?.firstOrNull()
                                                else -> null
                                            }
                                            loadLinksForItem(apiName, resp, firstEp)
                                        }
                                        isLoadingLinks = false
                                        if (links.isNotEmpty()) {
                                            availableLinks = links
                                            showLinkPicker = true
                                        }
                                    }
                                },
                            )
                        }

                        item { DetailsMetadata(resp) }

                        // Episodes
                        when (resp) {
                            is TvSeriesLoadResponse -> {
                                if (resp.episodes.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Episodes (${resp.episodes.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                    items(resp.episodes) { ep ->
                                        EpisodeRow(
                                            episode = ep,
                                            onClick = {
                                                scope.launch {
                                                    isLoadingLinks = true
                                                    val links = loadLinksForItem(apiName, resp, ep)
                                                    isLoadingLinks = false
                                                    if (links.size > 1) {
                                                        availableLinks = links
                                                        showLinkPicker = true
                                                    } else if (links.size == 1) {
                                                        onNavigateToPlayer(links[0].url, links[0].source)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            is AnimeLoadResponse -> {
                                val allEps = resp.episodes.values.flatten().sortedBy { it.episode }
                                if (allEps.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Episodes (${allEps.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                    items(allEps) { ep ->
                                        EpisodeRow(
                                            episode = ep,
                                            onClick = {
                                                scope.launch {
                                                    isLoadingLinks = true
                                                    val links = loadLinksForItem(apiName, resp, ep)
                                                    isLoadingLinks = false
                                                    if (links.size > 1) {
                                                        availableLinks = links
                                                        showLinkPicker = true
                                                    } else if (links.size == 1) {
                                                        onNavigateToPlayer(links[0].url, links[0].source)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }

                        // Recommendations
                        resp.recommendations?.takeIf { it.isNotEmpty() }?.let { recs ->
                            item {
                                Text(
                                    "More Like This",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(recs) { rec ->
                                        PosterCard(item = rec, onClick = { onNavigateToDetails(rec.apiName, rec.url) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLinkPicker && availableLinks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showLinkPicker = false },
            title = { Text("Choose a stream (${availableLinks.size})") },
            text = {
                Column {
                    availableLinks.forEach { link ->
                        TextButton(
                            onClick = {
                                showLinkPicker = false
                                onNavigateToPlayer(link.url, link.source)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    link.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "${Qualities.getStringByInt(link.quality)} - ${link.source}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLinkPicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailsHero(response: LoadResponse) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        val backdrop = response.backgroundPosterUrl ?: response.posterUrl
        if (backdrop != null) {
            AsyncImage(model = backdrop, contentDescription = response.name, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, MaterialTheme.colorScheme.background),
                ),
            ),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(response.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                response.year?.let { Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                response.score?.let { Text("* ${"%.1f".format(it.toFloat())}", color = MaterialTheme.colorScheme.secondary) }
                response.duration?.let { Text("$it min", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun DetailsActions(
    response: LoadResponse,
    isLoadingLinks: Boolean,
    onPlay: () -> Unit,
    onShowSources: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f),
            enabled = !isLoadingLinks,
        ) {
            if (isLoadingLinks) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading streams...")
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (response.type.isMovieType()) "Play" else "Play First Episode")
            }
        }
        OutlinedButton(
            onClick = onShowSources,
            enabled = !isLoadingLinks,
        ) {
            Icon(Icons.Filled.VideoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Sources")
        }
    }
}

@Composable
private fun DetailsMetadata(response: LoadResponse) {
    Column(modifier = Modifier.padding(16.dp)) {
        response.plot?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
        }
        response.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                episode.posterUrl?.let {
                    AsyncImage(model = it, contentDescription = episode.name, modifier = Modifier.fillMaxSize())
                }
                Text(
                    text = "S${episode.season ?: 1} E${episode.episode ?: 0}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name ?: "Episode ${episode.episode}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                episode.description?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

private suspend fun loadLinksForItem(
    apiName: String,
    response: LoadResponse,
    episode: Episode?,
): List<ExtractorLink> = withContext(Dispatchers.Default) {
    val api = APIHolder.getApiFromNameNull(apiName) ?: return@withContext emptyList()

    val data = when (response) {
        is MovieLoadResponse -> response.dataUrl.ifBlank { response.url }
        is TvSeriesLoadResponse -> episode?.data ?: response.episodes.firstOrNull()?.data ?: response.url
        is AnimeLoadResponse -> episode?.data ?: response.episodes.values.firstOrNull()?.firstOrNull()?.data ?: response.url
        else -> response.url
    }

    val links = mutableListOf<ExtractorLink>()
    val subs = mutableListOf<SubtitleFile>()
    try {
        api.loadLinks(data, isCasting = false, { subs.add(it) }, { links.add(it) })
    } catch (e: Throwable) {
        println("[Details] loadLinks failed: ${e.message}")
    }
    links
}
