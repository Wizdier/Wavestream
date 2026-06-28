package com.wizdier.wavestream.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wizdier.wavestream.R
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.ui.components.LoadingState
import com.wizdier.wavestream.ui.components.MovieRow
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_continue_watching),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueWatching, key = { it.rowId }) { entry ->
                        ContinueWatchingCard(entry = entry, onClick = { onOpenDetail(entry.providerId, entry.url) })
                    }
                }
            }
        }

        if (isLoading && homeLists.isEmpty()) {
            item { LoadingState(modifier = Modifier.fillMaxWidth().height(200.dp)) }
        }

        if (error != null && homeLists.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = error ?: stringResource(R.string.error_generic))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.load() }) { Text(stringResource(R.string.retry)) }
                }
            }
        }

        if (homeLists.isEmpty() && !isLoading && error == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.home_empty))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenSearch) { Text(stringResource(R.string.nav_search)) }
                }
            }
        }

        items(homeLists, key = { it.name }) { list ->
            MovieRow(
                title = list.name,
                items = list.items,
                onClick = { onOpenDetail(it.providerId, it.url) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    }
}

@Composable
private fun ContinueWatchingCard(entry: HistoryEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (!entry.backdropUrl.isNullOrEmpty()) {
            AsyncImage(
                model = entry.backdropUrl,
                contentDescription = entry.title,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
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
                text = "S${entry.season} E${entry.episode} · $pct%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
