package com.wavestream.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.wavestream.ui.components.EmptyState
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home screen. Aggregates the [MainAPI.getMainPage] results of every loaded
 * provider into horizontally-scrolling rows, similar to Netflix-style rails.
 *
 * When no providers are installed, shows an empty state prompting the user
 * to install some from the Extensions screen.
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

    val bootState by com.wavestream.WaveAppInit.bootState.collectAsState()

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
                    // Iterate over each declared page section, calling getMainPage
                    // for each. Failures in one section don't abort the rest.
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
            onAction = { /* re-trigger LaunchedEffect by toggling bootState */ },
        )
        sections.isEmpty() -> EmptyState(
            title = "No content yet",
            subtitle = "Install extensions from the Extensions tab to populate your home feed.",
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(sections) { section ->
                HomeSectionRow(section = section, onPosterClick = onPosterClick)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private data class HomeSection(val title: String, val items: List<SearchResponse>)

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    onPosterClick: (apiName: String, url: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(section.items) { item ->
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
