package com.wavestream.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.LoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Details screen for a single title. Calls [com.lagradost.cloudstream3.MainAPI.load]
 * on the source provider to fetch metadata, poster, plot, and episodes (for
 * series). The Play button delegates to the player screen with the first
 * available stream URL.
 */
@Composable
fun DetailsScreen(
    apiName: String,
    url: String,
    onPlay: (videoUrl: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<LoadResponse?>(null) }

    LaunchedEffect(apiName, url) {
        loading = true
        error = null
        try {
            val api = APIHolder.getApiFromNameNull(apiName)
                ?: APIHolder.getApiFromUrlNull(url)
            if (api == null) {
                error = "Provider '$apiName' not found"
                loading = false
                return@LaunchedEffect
            }
            val result = withContext(Dispatchers.Default) { api.load(url) }
            detail = result
            if (result == null) error = "Provider returned no data"
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load details"
        } finally {
            loading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loading -> LoadingIndicator(message = "Loading details…")
            error != null -> EmptyState(
                title = "Couldn't load details",
                subtitle = error,
                actionLabel = "Back",
                onAction = onBack,
            )
            detail != null -> DetailContent(
                detail = detail!!,
                onPlay = onPlay,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: LoadResponse,
    onPlay: (videoUrl: String) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            // Backdrop + back button
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = detail.backgroundPosterUrl ?: detail.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                val meta = buildString {
                    detail.year?.let { append(it) }
                    detail.duration?.let { if (isNotEmpty()) append(" · "); append("${it}min") }
                    detail.type?.let { if (isNotEmpty()) append(" · "); append(it.name) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                detail.plot?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(20.dp))
                // Play button — uses the dataUrl from the LoadResponse
                @Suppress("DEPRECATION_ERROR")
                val dataUrl = when (detail) {
                    is com.lagradost.cloudstream3.MovieLoadResponse -> detail.dataUrl
                    is com.lagradost.cloudstream3.LiveStreamLoadResponse -> detail.dataUrl
                    is com.lagradost.cloudstream3.TvSeriesLoadResponse -> detail.episodes.firstOrNull()?.data ?: ""
                    else -> ""
                }
                if (dataUrl.isNotBlank()) {
                    Button(
                        onClick = { onPlay(dataUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play")
                    }
                }
            }
        }

        // Episodes list for series
        @Suppress("DEPRECATION_ERROR")
        val isSeries = detail is com.lagradost.cloudstream3.TvSeriesLoadResponse
        if (isSeries) {
            @Suppress("DEPRECATION_ERROR")
            val episodes = (detail as com.lagradost.cloudstream3.TvSeriesLoadResponse).episodes
            if (episodes.isNotEmpty()) {
                item {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(episodes) { ep ->
                    EpisodeRow(
                        episode = ep,
                        onClick = { onPlay(ep.data) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            episode.posterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                val label = buildString {
                    episode.season?.let { append("S${it.toString().padStart(2, '0')}") }
                    episode.episode?.let {
                        if (isNotEmpty()) append(" ")
                        append("E${it.toString().padStart(2, '0')}")
                    }
                    if (isNotEmpty()) append(" · ")
                    append(episode.name ?: "Episode")
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                episode.description?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
