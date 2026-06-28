package com.wavestream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wavestream.features.details.DetailsScreen
import com.wavestream.features.downloads.DownloadsScreen
import com.wavestream.features.extensions.ExtensionsScreen
import com.wavestream.features.home.HomeScreen
import com.wavestream.features.library.LibraryScreen
import com.wavestream.features.player.PlayerScreen
import com.wavestream.features.search.SearchScreen
import com.wavestream.features.settings.SettingsScreen
import com.wavestream.ui.theme.WaveStreamTheme
import io.ktor.http.encodeURLPath

private fun String.urlEncode(): String = encodeURLPath()

/**
 * Root composable for the Wavestream app.
 *
 * Mirrors CloudStream's MainActivity + NuvioMobile's App.kt patterns:
 *   - Single activity / single composable
 *   - Navigation via Jetpack Navigation Compose
 *   - Bottom nav (phone) / nav rail (TV / landscape)
 *
 * Routes:
 *   home        → HomeScreen       (homepage with parallax hero + horizontal rails)
 *   search      → SearchScreen     (search across all providers + history)
 *   library     → LibraryScreen    (bookmarks + watch progress)
 *   downloads   → DownloadsScreen  (downloaded episodes/movies)
 *   settings    → SettingsScreen   (general / player / UI / providers / extensions)
 *   details/{apiName}/{url}  → DetailsScreen  (info page with episodes + cast + recommendations)
 *   player/{apiName}/{url}   → PlayerScreen   (full-screen ExoPlayer)
 *   extensions  → ExtensionsScreen  (manage installed plugins + Stremio addons)
 */
@Composable
fun App() {
    WaveStreamTheme(useDarkTheme = true) {
        val navController = rememberNavController()
        var currentRoute by remember { mutableStateOf("home") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                    currentRoute = "home"
                    HomeScreen(
                        onNavigateToDetails = { apiName, url ->
                            val encoded = url.urlEncode()
                            navController.navigate("details/$apiName/$encoded")
                        },
                        onNavigateToSearch = { navController.navigate("search") },
                    )
                }

                composable("search") {
                    currentRoute = "search"
                    SearchScreen(
                        onNavigateToDetails = { apiName, url ->
                            val encoded = url.urlEncode()
                            navController.navigate("details/$apiName/$encoded")
                        },
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable("library") {
                    currentRoute = "library"
                    LibraryScreen(
                        onNavigateToDetails = { apiName, url ->
                            val encoded = url.urlEncode()
                            navController.navigate("details/$apiName/$encoded")
                        },
                    )
                }

                composable("downloads") {
                    currentRoute = "downloads"
                    DownloadsScreen(
                        onNavigateToPlayer = { url ->
                            val encoded = url.urlEncode()
                            navController.navigate("player/local/$encoded")
                        },
                    )
                }

                composable("settings") {
                    currentRoute = "settings"
                    SettingsScreen(
                        onNavigateToExtensions = { navController.navigate("extensions") },
                    )
                }

                composable("extensions") {
                    currentRoute = "extensions"
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
                    val args = backStackEntry.arguments
                    @Suppress("UNCHECKED_CAST")
                    val argMap = args?.let { savedState ->
                        val cls = Class.forName("androidx.savedstate.SavedState")
                        val method = cls.getMethod("getMap")
                        method.invoke(savedState) as Map<String, Any?>
                    } ?: emptyMap<String, Any?>()
                    val apiName = argMap["apiName"] as? String ?: return@composable
                    val url = argMap["url"] as? String ?: return@composable
                    DetailsScreen(
                        apiName = apiName,
                        url = url,
                        onNavigateToPlayer = { linkUrl, source ->
                            val encoded = linkUrl.urlEncode()
                            val srcEncoded = source.urlEncode()
                            navController.navigate("player/$srcEncoded/$encoded")
                        },
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = "player/{source}/{url}",
                    arguments = listOf(
                        navArgument("source") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val args = backStackEntry.arguments
                    @Suppress("UNCHECKED_CAST")
                    val argMap = args?.let { savedState ->
                        val cls = Class.forName("androidx.savedstate.SavedState")
                        val method = cls.getMethod("getMap")
                        method.invoke(savedState) as Map<String, Any?>
                    } ?: emptyMap<String, Any?>()
                    val source = argMap["source"] as? String ?: return@composable
                    val url = argMap["url"] as? String ?: return@composable
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

