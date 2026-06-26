package com.wizdier.wavestream.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wizdier.wavestream.R
import com.wizdier.wavestream.ui.detail.DetailScreen
import com.wizdier.wavestream.ui.downloads.DownloadsScreen
import com.wizdier.wavestream.ui.favorites.FavoritesScreen
import com.wizdier.wavestream.ui.history.HistoryScreen
import com.wizdier.wavestream.ui.home.HomeScreen
import com.wizdier.wavestream.ui.player.PlayerActivity
import com.wizdier.wavestream.ui.search.SearchScreen
import com.wizdier.wavestream.ui.settings.SettingsScreen
import com.wizdier.wavestream.ui.settings.about.AboutScreen
import com.wizdier.wavestream.ui.settings.repos.RepoSettingsScreen
import com.wizdier.wavestream.ui.settings.sync.SyncSettingsScreen

sealed class WaveRoute(val route: String) {
    data object Home : WaveRoute("home")
    data object Search : WaveRoute("search")
    data object Downloads : WaveRoute("downloads")
    data object Settings : WaveRoute("settings")
    data object Favorites : WaveRoute("favorites")
    data object History : WaveRoute("history")
    data object Detail : WaveRoute("detail/{providerId}/{url}") {
        fun build(providerId: String, url: String) = "detail/$providerId/${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    data object Player : WaveRoute("player/{providerId}/{url}") {
        fun build(providerId: String, url: String) = "player/$providerId/${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    data object Repos : WaveRoute("settings/repos")
    data object Sync : WaveRoute("settings/sync")
    data object About : WaveRoute("settings/about")
}

private data class BottomItem(val route: WaveRoute, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun WaveNavHost() {
    val navController = rememberNavController()
    val backstack by navController.currentBackStackEntryAsState()
    val current = backstack?.destination

    val bottomItems = listOf(
        BottomItem(WaveRoute.Home, R.string.nav_home, Icons.Outlined.Home),
        BottomItem(WaveRoute.Search, R.string.nav_search, Icons.Outlined.Search),
        BottomItem(WaveRoute.Downloads, R.string.nav_downloads, Icons.Outlined.CloudDownload),
        BottomItem(WaveRoute.Settings, R.string.nav_settings, Icons.Outlined.Settings)
    )

    val showBottomBar = current?.route in bottomItems.map { it.route.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = current?.hierarchy?.any { it.route == item.route.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = WaveRoute.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(WaveRoute.Home.route) {
                HomeScreen(
                    onOpenDetail = { providerId, url ->
                        navController.navigate(WaveRoute.Detail.build(providerId, url))
                    },
                    onOpenSearch = { navController.navigate(WaveRoute.Search.route) }
                )
            }
            composable(WaveRoute.Search.route) {
                SearchScreen(onOpenDetail = { p, u -> navController.navigate(WaveRoute.Detail.build(p, u)) })
            }
            composable(WaveRoute.Downloads.route) {
                val ctx = LocalContext.current
                DownloadsScreen(
                    onOpenPlayer = { p, u ->
                        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_PROVIDER_ID, p)
                            putExtra(PlayerActivity.EXTRA_URL, u)
                        })
                    }
                )
            }
            composable(WaveRoute.Settings.route) {
                SettingsScreen(
                    onOpenRepos = { navController.navigate(WaveRoute.Repos.route) },
                    onOpenSync = { navController.navigate(WaveRoute.Sync.route) },
                    onOpenAbout = { navController.navigate(WaveRoute.About.route) },
                    onOpenFavorites = { navController.navigate(WaveRoute.Favorites.route) },
                    onOpenHistory = { navController.navigate(WaveRoute.History.route) }
                )
            }
            composable(WaveRoute.Favorites.route) {
                FavoritesScreen(onOpenDetail = { p, u -> navController.navigate(WaveRoute.Detail.build(p, u)) })
            }
            composable(WaveRoute.History.route) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { p, u -> navController.navigate(WaveRoute.Detail.build(p, u)) }
                )
            }
            composable(WaveRoute.Detail.route) { entry ->
                val ctx = LocalContext.current
                val providerId = entry.arguments?.getString("providerId").orEmpty()
                val url = java.net.URLDecoder.decode(entry.arguments?.getString("url").orEmpty(), "UTF-8")
                DetailScreen(
                    providerId = providerId,
                    url = url,
                    onPlay = { p, u ->
                        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_PROVIDER_ID, p)
                            putExtra(PlayerActivity.EXTRA_URL, u)
                        })
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(WaveRoute.Repos.route) { RepoSettingsScreen(onBack = { navController.popBackStack() }) }
            composable(WaveRoute.Sync.route) { SyncSettingsScreen(onBack = { navController.popBackStack() }) }
            composable(WaveRoute.About.route) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
