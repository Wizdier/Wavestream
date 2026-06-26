package com.wizdier.wavestream.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.api.LoadResponse
import com.wizdier.wavestream.data.api.VideoLink
import com.wizdier.wavestream.ui.components.BlurBackdrop
import com.wizdier.wavestream.ui.components.EmptyState
import com.wizdier.wavestream.ui.components.LoadingState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    providerId: String,
    url: String,
    onPlay: (providerId: String, url: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val links by viewModel.links.collectAsState()
    val selectedLink by viewModel.selectedLink.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    androidx.compose.runtime.LaunchedEffect(providerId, url) {
        viewModel.load(providerId, url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            DetailState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is DetailState.Error -> EmptyState(message = s.message, modifier = Modifier.padding(padding))
            is DetailState.Loaded -> DetailBody(
                data = s.data,
                links = links,
                selectedLink = selectedLink,
                isFavorite = isFavorite,
                onSelectLink = viewModel::selectLink,
                onToggleFavorite = viewModel::toggleFavorite,
                onPlay = { onPlay(s.data.providerId, s.data.url) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun DetailBody(
    data: LoadResponse,
    links: List<VideoLink>,
    selectedLink: VideoLink?,
    isFavorite: Boolean,
    onSelectLink: (VideoLink) -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                BlurBackdrop(url = data.backdropUrl ?: data.posterUrl, fadeTo = MaterialTheme.colorScheme.background)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!data.posterUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = data.posterUrl,
                            contentDescription = data.name,
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = data.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        data.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        if (data.tags.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                data.tags.take(4).forEach { tag ->
                                    AssistChip(onClick = {}, label = { Text(tag) })
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.detail_play))
                }
                val sourceMenuOpen = remember { mutableStateOf(false) }
                Box {
                    ElevatedButton(onClick = { sourceMenuOpen.value = true }) {
                        Text(selectedLink?.name ?: stringResource(R.string.detail_sources))
                    }
                    DropdownMenu(
                        expanded = sourceMenuOpen.value,
                        onDismissRequest = { sourceMenuOpen.value = false }
                    ) {
                        if (links.isEmpty()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.detail_no_sources)) }, onClick = {})
                        } else {
                            links.forEach { link ->
                                DropdownMenuItem(
                                    text = { Text("${link.name} · ${link.quality.label}") },
                                    onClick = {
                                        onSelectLink(link)
                                        sourceMenuOpen.value = false
                                    }
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.detail_add_to_favorites),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (!data.description.isNullOrEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.detail_synopsis),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (data.episodes.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.detail_episodes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(data.episodes.size) { i ->
                val ep = data.episodes[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "E${ep.episode}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(40.dp)
                    )
                    Column {
                        Text(ep.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        ep.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
