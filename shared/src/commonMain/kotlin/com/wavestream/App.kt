package com.wavestream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.components.WaveBottomBar
import com.wavestream.ui.components.WaveTab
import com.wavestream.ui.screens.details.DetailsScreen
import com.wavestream.ui.screens.downloads.DownloadsScreen
import com.wavestream.ui.screens.extensions.ExtensionsScreen
import com.wavestream.ui.screens.home.HomeScreen
import com.wavestream.ui.screens.library.LibraryScreen
import com.wavestream.ui.screens.player.PlayerScreen
import com.wavestream.ui.screens.search.SearchScreen
import com.wavestream.ui.screens.settings.SettingsScreen
import com.wavestream.ui.theme.WaveTheme

/**
 * Root composable. Hosts the NavHost and bottom navigation bar.
 *
 * Routes:
 * - `home`, `search`, `library`, `downloads`, `extensions` — bottom-nav tabs
 * - `settings` — pushed from any top-app-bar gear icon
 * - `details/{apiName}/{url}` — pushed when a poster is tapped
 * - `player/{videoUrl}` — pushed when an episode / play is tapped
 *
 * Use [Routes] constants instead of string literals everywhere.
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val EXTENSIONS = "extensions"
    const val SETTINGS = "settings"
    const val DETAILS = "details"
    const val PLAYER = "player"
}

/**
 * Side-channel for navigation arguments. The Compose-Multiplatform
 * navigation library's `Bundle` doesn't expose `getString` on non-Android
 * targets in this version, so we pass complex args through this singleton
 * keyed by route. The NavHost clears each slot after reading.
 */
object NavArgs {
    @Volatile var detailApiName: String? = null
    @Volatile var detailUrl: String? = null
    @Volatile var playerUrl: String? = null

    fun setDetail(apiName: String, url: String) {
        detailApiName = apiName
        detailUrl = url
    }

    fun setPlayer(url: String) {
        playerUrl = url
    }

    fun consumeDetail(): Pair<String, String> {
        val a = detailApiName.orEmpty()
        val u = detailUrl.orEmpty()
        detailApiName = null
        detailUrl = null
        return a to u
    }

    fun consumePlayer(): String {
        val u = playerUrl.orEmpty()
        playerUrl = null
        return u
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val bootState by WaveAppInit.bootState.collectAsState()

    // Tabs that should show the bottom bar. Pushed routes (details, player,
    // settings) hide it for a more focused UX.
    val showBottomBar = currentRoute in WaveTab.entries.map { it.route }

    WaveTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                // Hide the top app bar on the player route — full-screen.
                if (currentRoute != Routes.PLAYER && currentRoute != null) {
                    TopAppBar(
                        title = { Text("Wavestream") },
                        actions = {
                            IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    WaveBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (!bootState.stage.isReady && currentRoute == Routes.HOME) {
                    LoadingIndicator(message = bootState.message ?: "Booting…")
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onPosterClick = { apiName, url ->
                                NavArgs.setDetail(apiName, url)
                                navController.navigate(Routes.DETAILS)
                            },
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            onPosterClick = { apiName, url ->
                                NavArgs.setDetail(apiName, url)
                                navController.navigate(Routes.DETAILS)
                            },
                        )
                    }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onPosterClick = { apiName, url ->
                                NavArgs.setDetail(apiName, url)
                                navController.navigate(Routes.DETAILS)
                            },
                        )
                    }
                    composable(Routes.DOWNLOADS) { DownloadsScreen() }
                    composable(Routes.EXTENSIONS) {
                        ExtensionsScreen(
                            onRescanRequested = { WaveAppInit.rescan() },
                        )
                    }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                    composable(Routes.DETAILS) {
                        val (apiName, url) = NavArgs.consumeDetail()
                        DetailsScreen(
                            apiName = apiName,
                            url = url,
                            onPlay = { videoUrl ->
                                NavArgs.setPlayer(videoUrl)
                                navController.navigate(Routes.PLAYER)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.PLAYER) {
                        val videoUrl = NavArgs.consumePlayer()
                        PlayerScreen(
                            videoUrl = videoUrl,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
