package com.wavestream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.wavestream.ui.components.WaveBottomBar
import com.wavestream.ui.theme.WaveStreamTheme
import com.wavestream.ui.screens.details.DetailsScreen
import com.wavestream.ui.screens.downloads.DownloadsScreen
import com.wavestream.ui.screens.extensions.ExtensionsScreen
import com.wavestream.ui.screens.home.HomeScreen
import com.wavestream.ui.screens.library.LibraryScreen
import com.wavestream.ui.screens.player.PlayerScreen
import com.wavestream.ui.screens.search.SearchScreen
import com.wavestream.ui.screens.settings.SettingsScreen

private fun String.urlEncode(): String {
    val sb = StringBuilder()
    for (byte in toByteArray()) {
        val v = byte.toInt() and 0xFF
        val c = v.toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
            sb.append(c)
        } else {
            sb.append('%')
            sb.append("0123456789ABCDEF"[v shr 4])
            sb.append("0123456789ABCDEF"[v and 0x0F])
        }
    }
    return sb.toString()
}

private fun String.urlDecode(): String {
    val sb = StringBuilder()
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '%' && i + 2 < length) {
            val hex = substring(i + 1, i + 3)
            val v = hex.toInt(16)
            sb.append(v.toChar())
            i += 3
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private val mainRoutes = setOf("home", "search", "library", "downloads", "settings")

@Composable
fun App() {
    WaveStreamTheme(useDarkTheme = true) {
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (currentRoute in mainRoutes) {
                    WaveBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                composable("home") {
                    HomeScreen(
                        onNavigateToDetails = { apiName, url ->
                            navController.navigate("details/${apiName.urlEncode()}/${url.urlEncode()}")
                        },
                        onNavigateToSearch = {
                            navController.navigate("search") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }

                composable("search") {
                    SearchScreen(
                        onNavigateToDetails = { apiName, url ->
                            navController.navigate("details/${apiName.urlEncode()}/${url.urlEncode()}")
                        },
                    )
                }

                composable("library") {
                    LibraryScreen(
                        onNavigateToDetails = { apiName, url ->
                            navController.navigate("details/${apiName.urlEncode()}/${url.urlEncode()}")
                        },
                    )
                }

                composable("downloads") {
                    DownloadsScreen(
                        onNavigateToPlayer = { url ->
                            navController.navigate("player/local/${url.urlEncode()}")
                        },
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onNavigateToExtensions = { navController.navigate("extensions") },
                    )
                }

                composable("extensions") {
                    ExtensionsScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = "details/{apiName}/{url}",
                    arguments = listOf(
                        navArgument("apiName") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val apiName = backStackEntry.arguments?.read { getStringOrNull("apiName") }?.urlDecode() ?: return@composable
                    val url = backStackEntry.arguments?.read { getStringOrNull("url") }?.urlDecode() ?: return@composable
                    DetailsScreen(
                        apiName = apiName,
                        url = url,
                        onNavigateToPlayer = { linkUrl, source ->
                            navController.navigate("player/${source.urlEncode()}/${linkUrl.urlEncode()}")
                        },
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDetails = { detailApiName, detailUrl ->
                            navController.navigate("details/${detailApiName.urlEncode()}/${detailUrl.urlEncode()}")
                        },
                    )
                }

                composable(
                    route = "player/{source}/{url}",
                    arguments = listOf(
                        navArgument("source") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val source = backStackEntry.arguments?.read { getStringOrNull("source") }?.urlDecode() ?: return@composable
                    val url = backStackEntry.arguments?.read { getStringOrNull("url") }?.urlDecode() ?: return@composable
                    PlayerScreen(
                        source = source,
                        url = url,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
