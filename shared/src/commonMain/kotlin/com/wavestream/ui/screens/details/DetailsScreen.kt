package com.wavestream.ui.screens.details

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.isMovieType
import com.wavestream.ui.components.ErrorState
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.components.PosterCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    apiName: String,
    url: String,
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String, String) -> Unit = { _, _ -> },
) {
    var resp by remember { mutableStateOf<LoadResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apiName, url) {
        isLoading = true
        error = null
        try {
            val api = APIHolder.getApiFromNameNull(apiName)
            if (api == null) {
                error = "Provider not found"
            } else {
                resp = api.load(url)
            }
        } catch (e: Throwable) {
            error = e.message
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = resp?.name ?: "Loading...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingIndicator()
                error != null -> ErrorState(message = error!!, onRetry = onNavigateBack)
                resp != null -> {
                    val r = resp!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        item { Header(r) }
                        item { ActionRow(r, onNavigateToPlayer, apiName) }
                        item { OverviewSection(r) }

                        when (r) {
                            is TvSeriesLoadResponse -> {
                                if (r.episodes.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Episodes (${r.episodes.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                    items(r.episodes) { ep ->
                                        EpisodeRow(ep)
                                    }
                                }
                            }
                            else -> {}
                        }

                        r.recommendations?.takeIf { it.isNotEmpty() }?.let { recs ->
                            item {
                                Text(
                                    text = "More Like This",
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
                                            onClick = { onNavigateToDetails(rec.apiName, rec.url) },
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
private fun Header(r: LoadResponse) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        val backdrop = r.backgroundPosterUrl ?: r.posterUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = r.name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = r.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                r.year?.let {
                    Text(
                        text = "$it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                r.score?.let {
                    Text(
                        text = "* ${"%.1f".format(it.toFloat())}",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                r.duration?.let {
                    Text(
                        text = "$it min",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    r: LoadResponse,
    onNavigateToPlayer: (String, String) -> Unit,
    apiName: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                val dataUrl = when (r) {
                    is MovieLoadResponse -> r.dataUrl
                    is TvSeriesLoadResponse -> r.episodes.firstOrNull()?.data
                    else -> null
                }
                if (dataUrl != null) {
                    onNavigateToPlayer(dataUrl, apiName)
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = if (r.type.isMovieType()) "Play" else "Play First")
        }

        OutlinedButton(onClick = { /* sources */ }) {
            Icon(imageVector = Icons.Filled.VideoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Sources")
        }
    }
}

@Composable
private fun OverviewSection(r: LoadResponse) {
    Column(modifier = Modifier.padding(16.dp)) {
        r.plot?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        r.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text(text = tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable {},
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = "S${ep.season ?: 1} E${ep.episode ?: 0}",
                    modifier = Modifier.padding(4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ep.name ?: "Episode ${ep.episode}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
