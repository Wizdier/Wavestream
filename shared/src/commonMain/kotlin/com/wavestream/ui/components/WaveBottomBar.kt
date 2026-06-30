package com.wavestream.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Top-level destinations exposed by the bottom navigation bar.
 *
 * Keep this enum as the single source of truth for tab identity — screens
 * and the NavHost both key off [route].
 */
enum class WaveTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Search("search", "Search", Icons.Outlined.Search),
    Library("library", "Library", Icons.Outlined.VideoLibrary),
    Downloads("downloads", "Downloads", Icons.Outlined.CloudDownload),
    Extensions("extensions", "Extensions", Icons.Outlined.Extension),
    ;

    companion object {
        fun fromRoute(route: String?): WaveTab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * Material3 bottom navigation bar with five tabs. Pass the current route
 * and a callback to navigate. The Settings screen is reached via the
 * top-app-bar gear icon, not from this bar.
 */
@Composable
fun WaveBottomBar(
    currentRoute: String?,
    onTabSelected: (WaveTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        WaveTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(text = tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
