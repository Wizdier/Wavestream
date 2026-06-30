package com.wavestream.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class WaveNavItem(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

val waveNavItems = listOf(
    WaveNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    WaveNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
    WaveNavItem("library", "Library", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    WaveNavItem("downloads", "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    WaveNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun WaveBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        waveNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(selected, { onNavigate(item.route) }, icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, item.label, Modifier.size(24.dp)) }, label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
