package com.wavestream.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import com.wavestream.api.*
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Details screen — mirrors CloudStream's ResultFragment.
 *
 * Layout:
 *   - Backdrop hero with poster, title, year, rating, plot
 *   - Action buttons: Play, Trailer, Bookmark, Favorite, Subscribe
 *   - Cast row (horizontal)
 *   - Episode list (for TV series/anime)
 *   - Recommendations row
 *
 * Data flow:
 *   - User taps a search result → navigate to details/{apiName}/{url}
 *   - DetailsScreen calls APIRepository(api).load(url) → LoadResponse
 *   - Displays metadata, episodes, etc.
 *   - When user taps an episode → calls loadLinks() → list of ExtractorLinks
 *   - User picks a link (or auto-selects best) → navigate to player/{source}/{url}
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    apiName: String,
    url: String,
    onNavigateToPlayer: (url: String, source: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loadResponse by remember { mutableStateOf<LoadResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var availableLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var showLinkPicker by remember { mutableStateOf(false) }

    LaunchedEffect(apiName, url) {
        scope.launch {
            isLoading = true
            error = null
            try {
                val api = APIHolder.getApiFromName(apiName)
                if (api == null) {
                    error = "Provider $apiName not found"
                } else {
                    val repo = APIRepository(api)
                    when (val res = repo.load(url)) {
                        is Resource.Success -> loadResponse = res.value
                        is Resource.Failure -> error = res.error.message ?: "Failed to load"
                        is Resource.Loading -> {}
                    }
                }
            } catch (e: Throwable) {
                error = e.message
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loadResponse?.name ?: "Loading...") },
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
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Error: $error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateBack) { Text("Go back") }
                    }
                }
                loadResponse != null -> {
                    val resp = loadResponse ?: return@Box
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        // Hero
                        item { DetailsHero(resp) }

                        // Action buttons
                        item {
                            DetailsActions(
                                response = resp,
                                onPlay = {
                                    scope.launch { playItem(apiName, resp, null, onNavigateToPlayer) }
                                },
                                onTrailer = { /* TODO: trailer player */ },
                            )
                        }

                        // Metadata
                        item { DetailsMetadata(resp) }

                        // Cast
                        resp.actors?.takeIf { it.isNotEmpty() }?.let { actors ->
                            item {
                                Text(
                                    "Cast",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(actors) { actorData ->
                                        CastCard(actorData)
                                    }
                                }
                            }
                        }

                        // Episodes (for series)
                        when (resp) {
                            is TvSeriesLoadResponse -> {
                                item {
                                    Text(
                                        "Episodes (${resp.episodes.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                                items(resp.episodes.withIndex().toList()) { (idx, ep) ->
                                    EpisodeRow(
                                        episode = ep,
                                        onClick = {
                                            scope.launch {
                                                playItem(apiName, resp, ep, onNavigateToPlayer)
                                            }
                                        },
                                    )
                                }
                            }
                            is AnimeLoadResponse -> {
                                val allEps = resp.episodes.values.flatten().sortedBy { it.episode }
                                item {
                                    Text(
                                        "Episodes (${allEps.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                                items(allEps.withIndex().toList()) { (idx, ep) ->
                                    EpisodeRow(
                                        episode = ep,
                                        onClick = {
                                            scope.launch {
                                                playItem(apiName, resp, ep, onNavigateToPlayer)
                                            }
                                        },
                                    )
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
                                        PosterCard(
                                            item = rec,
                                            onClick = { /* navigate to details */ },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsHero(response: LoadResponse) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
    ) {
        // Backdrop
        val backdrop = response.backgroundPosterUrl ?: response.posterUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = response.name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
            )

        // Title + meta
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = response.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                response.year?.let { Text("${it}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                response.score?.let { Text("★ ${"%.1f".format(it)}", color = MaterialTheme.colorScheme.secondary) }
                response.duration?.let { Text("${it} min", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                response.contentRating?.let {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsActions(
    response: LoadResponse,
    onPlay: () -> Unit,
    onTrailer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (response.type.isMovieType()) "Play" else "Play First Episode")
        }
        OutlinedButton(onClick = onTrailer) {
            Text("Trailer")
        }
    }
}

@Composable
private fun DetailsMetadata(response: LoadResponse) {
    Column(modifier = Modifier.padding(16.dp)) {
        response.plot?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        response.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CastCard(actor: ActorData) {
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            actor.actor.image?.let {
                AsyncImage(
                    model = it,
                    contentDescription = actor.actor.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = actor.actor.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface,
        )
        actor.roleString?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode thumbnail
            Box(
                modifier = Modifier
                    .size(120.dp, 68.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                episode.posterUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Play an item — calls loadLinks on the provider, picks the best link, navigates to player.
 */
private suspend fun playItem(
    apiName: String,
    response: LoadResponse,
    episode: Episode?,
    onNavigateToPlayer: (url: String, source: String) -> Unit,
) {
    val api = APIHolder.getApiFromName(apiName) ?: return
    val repo = APIRepository(api)

    val data = when (response) {
        is MovieLoadResponse -> response.data ?: response.url
        is TvSeriesLoadResponse -> episode?.data ?: response.episodes.firstOrNull()?.data ?: response.url
        is AnimeLoadResponse -> episode?.data ?: response.episodes.values.firstOrNull()?.firstOrNull()?.data ?: response.url
        else -> response.url
    }

    val links = mutableListOf<ExtractorLink>()
    val subs = mutableListOf<SubtitleFile>()
    repo.loadLinks(data, isCasting = false, { subs.add(it) }, { links.add(it) })

    if (links.isNotEmpty()) {
        // Pick the highest-quality link
        val best = links.maxByOrNull { it.quality } ?: links.first()
        onNavigateToPlayer(best.url, best.source)
    }
}
