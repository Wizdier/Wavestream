package com.wavestream.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.WaveAppInit
import com.wavestream.ui.settings.Preference
import com.wavestream.ui.settings.widget.PreferenceItemWidget
import com.wavestream.ui.settings.widget.PreferenceScaffold

/**
 * Root settings screen — a list of category cards that push into
 * sub-screens when tapped. Modeled after Anikku's `SettingsMainScreen`.
 *
 * Categories:
 *  - General       — default tab, jsdelivr proxy, auto-rescan
 *  - Appearance    — theme, poster size, badges
 *  - Streaming     — quality, prefer Stremio, prefer HLS
 *  - Player        — playback speed, gestures, PiP, remember position
 *  - Subtitles     — enable, language, font size
 *  - Extensions    — quick link to the Extensions tab
 *  - Network       — timeout, concurrent requests
 *  - Advanced      — verbose logging, restore defaults
 *  - About         — version, plugins loaded, data dir
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    var selectedCategory: SettingsCategory? by remember { mutableStateOf(null) }

    val current = selectedCategory
    if (current != null) {
        SubSettingsScreen(
            category = current,
            onBack = { selectedCategory = null },
            modifier = modifier,
        )
    } else {
        SettingsMainScreen(
            onCategoryClick = { selectedCategory = it },
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsMainScreen(
    onCategoryClick: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bootState by WaveAppInit.bootState.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lazyItems(SettingsCategory.entries) { category ->
            CategoryRow(
                category = category,
                onClick = { onCategoryClick(category) },
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = "Wavestream · ${bootState.message ?: "ready"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp),
            )
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubSettingsScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        PreferenceScaffold(
            preferences = category.buildPreferences(),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val buildPreferences: @Composable () -> List<Preference>,
) {
    GENERAL(
        title = "General",
        subtitle = "Default tab, auto-rescan, jsDelivr proxy",
        icon = Icons.Outlined.Tune,
        buildPreferences = {
            val bootState by WaveAppInit.bootState.collectAsState()
            listOf(
                Preference.Group(
                    title = "Startup",
                    items = listOf(
                        Preference.List(
                            title = "Default tab",
                            subtitle = "Tab opened on app launch.",
                            icon = Icons.Outlined.Tune,
                            entries = mapOf(
                                "home" to "Home",
                                "search" to "Search",
                                "library" to "Library",
                                "downloads" to "Downloads",
                                "extensions" to "Extensions",
                            ),
                            selected = com.wavestream.ui.settings.WavePreferences.defaultTab,
                            onSelected = { com.wavestream.ui.settings.WavePreferences.defaultTab = it },
                        ),
                        Preference.Switch(
                            title = "Auto-rescan extensions on boot",
                            subtitle = "Re-scan the Extensions directory every time the app starts.",
                            icon = Icons.Outlined.Restore,
                            checked = com.wavestream.ui.settings.WavePreferences.autoRescanOnBoot,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.autoRescanOnBoot = it },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Network optimizations",
                    items = listOf(
                        Preference.Switch(
                            title = "Use jsDelivr proxy for GitHub",
                            subtitle = "Faster downloads of CS repositories hosted on raw.githubusercontent.com.",
                            icon = Icons.Outlined.Public,
                            checked = com.wavestream.ui.settings.WavePreferences.jsdelivrProxy,
                            onCheckedChange = {
                                com.wavestream.ui.settings.WavePreferences.jsdelivrProxy = it
                                com.lagradost.cloudstream3.plugins.RepositoryManager.useJsDelivrProxy = it
                            },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Status",
                    items = listOf(
                        Preference.Info(
                            title = "Boot stage: ${bootState.stage}",
                            subtitle = bootState.message ?: "—",
                            icon = Icons.Outlined.Info,
                        ),
                    ),
                ),
            )
        },
    ),
    APPEARANCE(
        title = "Appearance",
        subtitle = "Theme, poster size, badges",
        icon = Icons.Outlined.Brightness6,
        buildPreferences = {
            listOf(
                Preference.Group(
                    title = "Theme",
                    items = listOf(
                        Preference.List(
                            title = "Theme mode",
                            subtitle = "Dark mode is recommended for media apps.",
                            icon = Icons.Outlined.Brightness6,
                            entries = mapOf(
                                "dark" to "Dark (default)",
                                "light" to "Light",
                                "system" to "Follow system",
                            ),
                            selected = com.wavestream.ui.settings.WavePreferences.themeMode,
                            onSelected = { com.wavestream.ui.settings.WavePreferences.themeMode = it },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Posters",
                    items = listOf(
                        Preference.Slider(
                            title = "Poster card width",
                            subtitle = "Width of each poster in the grid, in dp.",
                            icon = Icons.Outlined.Tune,
                            value = com.wavestream.ui.settings.WavePreferences.posterCardWidth.toFloat(),
                            valueRange = 80f..180f,
                            steps = 9,
                            onValueChange = {
                                com.wavestream.ui.settings.WavePreferences.posterCardWidth = it.toInt()
                            },
                        ),
                        Preference.Switch(
                            title = "Show quality badges",
                            subtitle = "Overlay resolution/quality on poster cards.",
                            icon = Icons.Outlined.Tune,
                            checked = com.wavestream.ui.settings.WavePreferences.showQualityBadges,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.showQualityBadges = it },
                        ),
                    ),
                ),
            )
        },
    ),
    STREAMING(
        title = "Streaming",
        subtitle = "Quality, format preference",
        icon = Icons.Outlined.PlayCircle,
        buildPreferences = {
            listOf(
                Preference.Group(
                    title = "Quality",
                    items = listOf(
                        Preference.List(
                            title = "Default quality",
                            subtitle = "Quality preferred when multiple streams are available.",
                            icon = Icons.Outlined.PlayCircle,
                            entries = mapOf(
                                "auto" to "Auto (best available)",
                                "4k" to "4K UHD",
                                "hdr" to "HDR",
                                "hd" to "HD 1080p+",
                                "sd" to "SD 480p+",
                            ),
                            selected = com.wavestream.ui.settings.WavePreferences.defaultQuality,
                            onSelected = { com.wavestream.ui.settings.WavePreferences.defaultQuality = it },
                        ),
                        Preference.Switch(
                            title = "Prefer HLS over MP4",
                            subtitle = "HLS streams adapt to bandwidth automatically.",
                            icon = Icons.Outlined.PlayCircle,
                            checked = com.wavestream.ui.settings.WavePreferences.preferHlsOverMp4,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.preferHlsOverMp4 = it },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Source preference",
                    items = listOf(
                        Preference.Switch(
                            title = "Prefer Stremio streams",
                            subtitle = "When both CS and Stremio sources are available, prefer Stremio.",
                            icon = Icons.Outlined.Extension,
                            checked = com.wavestream.ui.settings.WavePreferences.preferStremioStreams,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.preferStremioStreams = it },
                        ),
                    ),
                ),
            )
        },
    ),
    PLAYER(
        title = "Player",
        subtitle = "Playback speed, gestures, PiP",
        icon = Icons.Outlined.PlayCircle,
        buildPreferences = {
            listOf(
                Preference.Group(
                    title = "Playback",
                    items = listOf(
                        Preference.Slider(
                            title = "Default playback speed",
                            subtitle = "Applied when a new video starts.",
                            icon = Icons.Outlined.PlayCircle,
                            value = com.wavestream.ui.settings.WavePreferences.playbackSpeed,
                            valueRange = 0.25f..3.0f,
                            steps = 10,
                            onValueChange = { com.wavestream.ui.settings.WavePreferences.playbackSpeed = it },
                        ),
                        Preference.Switch(
                            title = "Remember playback position",
                            subtitle = "Resume from where you left off across app restarts.",
                            icon = Icons.Outlined.Restore,
                            checked = com.wavestream.ui.settings.WavePreferences.rememberPlaybackPosition,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.rememberPlaybackPosition = it },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Gestures",
                    items = listOf(
                        Preference.Switch(
                            title = "Enable player gestures",
                            subtitle = "Swipe to adjust brightness, volume, and seek.",
                            icon = Icons.Outlined.Tune,
                            checked = com.wavestream.ui.settings.WavePreferences.enableGestures,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.enableGestures = it },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Picture-in-Picture",
                    items = listOf(
                        Preference.Switch(
                            title = "Enable PiP",
                            subtitle = "Mini player when navigating away from the player screen (Android).",
                            icon = Icons.Outlined.PlayCircle,
                            checked = com.wavestream.ui.settings.WavePreferences.pictureInPicture,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.pictureInPicture = it },
                        ),
                    ),
                ),
            )
        },
    ),
    SUBTITLES(
        title = "Subtitles",
        subtitle = "Enable, language, font size",
        icon = Icons.Outlined.Subtitles,
        buildPreferences = {
            listOf(
                Preference.Group(
                    title = "Subtitles",
                    items = listOf(
                        Preference.Switch(
                            title = "Enable subtitles",
                            subtitle = "Overlay subtitles on video when available.",
                            icon = Icons.Outlined.Subtitles,
                            checked = com.wavestream.ui.settings.WavePreferences.subtitlesEnabled,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.subtitlesEnabled = it },
                        ),
                        Preference.List(
                            title = "Subtitle language",
                            subtitle = "Preferred language for embedded and Stremio subtitles.",
                            icon = Icons.Outlined.Subtitles,
                            entries = mapOf(
                                "en" to "English",
                                "es" to "Español",
                                "fr" to "Français",
                                "de" to "Deutsch",
                                "it" to "Italiano",
                                "pt" to "Português",
                                "ru" to "Русский",
                                "ja" to "日本語",
                                "ko" to "한국어",
                                "zh" to "中文",
                                "ar" to "العربية",
                                "hi" to "हिन्दी",
                            ),
                            selected = com.wavestream.ui.settings.WavePreferences.subtitleLanguage,
                            onSelected = { com.wavestream.ui.settings.WavePreferences.subtitleLanguage = it },
                        ),
                        Preference.Slider(
                            title = "Subtitle font size",
                            subtitle = "Relative scale, 1.0 = default.",
                            icon = Icons.Outlined.Tune,
                            value = com.wavestream.ui.settings.WavePreferences.subtitleFontSize,
                            valueRange = 0.5f..2.5f,
                            steps = 19,
                            onValueChange = { com.wavestream.ui.settings.WavePreferences.subtitleFontSize = it },
                        ),
                    ),
                ),
            )
        },
    ),
    NETWORK(
        title = "Network",
        subtitle = "Timeout, concurrent requests",
        icon = Icons.Outlined.Public,
        buildPreferences = {
            listOf(
                Preference.Group(
                    title = "HTTP",
                    items = listOf(
                        Preference.Slider(
                            title = "Request timeout (seconds)",
                            subtitle = "Maximum time to wait for a provider to respond.",
                            icon = Icons.Outlined.Public,
                            value = com.wavestream.ui.settings.WavePreferences.requestTimeoutSeconds.toFloat(),
                            valueRange = 5f..120f,
                            steps = 22,
                            onValueChange = {
                                com.wavestream.ui.settings.WavePreferences.requestTimeoutSeconds = it.toInt()
                            },
                        ),
                        Preference.Slider(
                            title = "Concurrent requests",
                            subtitle = "Number of providers queried in parallel during search.",
                            icon = Icons.Outlined.Tune,
                            value = com.wavestream.ui.settings.WavePreferences.concurrentRequests.toFloat(),
                            valueRange = 1f..32f,
                            steps = 30,
                            onValueChange = {
                                com.wavestream.ui.settings.WavePreferences.concurrentRequests = it.toInt()
                            },
                        ),
                    ),
                ),
            )
        },
    ),
    ADVANCED(
        title = "Advanced",
        subtitle = "Logging, restore defaults",
        icon = Icons.Outlined.Code,
        buildPreferences = {
            listOf(
                Preference.Group(
                    title = "Diagnostics",
                    items = listOf(
                        Preference.Switch(
                            title = "Verbose logging",
                            subtitle = "Print every provider request and parse result to stdout.",
                            icon = Icons.Outlined.Code,
                            checked = com.wavestream.ui.settings.WavePreferences.verboseLogging,
                            onCheckedChange = { com.wavestream.ui.settings.WavePreferences.verboseLogging = it },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Maintenance",
                    items = listOf(
                        Preference.Text(
                            title = "Restore default repos and addons",
                            subtitle = "Re-seeds the curated list of CloudStream repositories and Stremio addons.",
                            icon = Icons.Outlined.Restore,
                            onClick = { WaveAppInit.restoreDefaults() },
                        ),
                        Preference.Text(
                            title = "Rescan extensions now",
                            subtitle = "Force a reload of the Extensions directory.",
                            icon = Icons.Outlined.Restore,
                            onClick = { WaveAppInit.rescan() },
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Security",
                    items = listOf(
                        Preference.Info(
                            title = "Safe mode",
                            subtitle = "Disabled — no plugin errors detected.",
                            icon = Icons.Outlined.Security,
                        ),
                    ),
                ),
            )
        },
    ),
    ABOUT(
        title = "About",
        subtitle = "Version, plugins loaded, data directory",
        icon = Icons.Outlined.Info,
        buildPreferences = {
            val bootState by WaveAppInit.bootState.collectAsState()
            listOf(
                Preference.Group(
                    title = "App",
                    items = listOf(
                        Preference.Info(
                            title = "Wavestream 1.0.0",
                            subtitle = "A Compose Multiplatform fork of CloudStream 3.",
                            icon = Icons.Outlined.Info,
                        ),
                        Preference.Info(
                            title = "Boot stage",
                            subtitle = bootState.stage.name,
                            icon = Icons.Outlined.Restore,
                        ),
                        Preference.Info(
                            title = "Plugins loaded",
                            subtitle = "${bootState.pluginsLoaded} plugins · ${bootState.providersLoaded} providers",
                            icon = Icons.Outlined.Extension,
                        ),
                        Preference.Info(
                            title = "Default repos seeded",
                            subtitle = "${bootState.reposSeeded} repos · ${bootState.addonsSeeded} Stremio addons",
                            icon = Icons.Outlined.Download,
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Storage",
                    items = listOf(
                        Preference.Info(
                            title = "Data directory",
                            subtitle = com.wavestream.platform.wavePlatform.dataDir.absolutePath,
                            icon = Icons.Outlined.Code,
                        ),
                        Preference.Info(
                            title = "Extensions directory",
                            subtitle = com.wavestream.platform.wavePlatform.extensionsDir.absolutePath,
                            icon = Icons.Outlined.Extension,
                        ),
                        Preference.Info(
                            title = "Downloads directory",
                            subtitle = com.wavestream.platform.wavePlatform.downloadsDir.absolutePath,
                            icon = Icons.Outlined.Download,
                        ),
                    ),
                ),
                Preference.Group(
                    title = "Credits",
                    items = listOf(
                        Preference.Info(
                            title = "CloudStream 3",
                            subtitle = "Upstream project — github.com/recloudstream/cloudstream",
                            icon = Icons.Outlined.Public,
                        ),
                        Preference.Info(
                            title = "Compose Multiplatform",
                            subtitle = "UI framework — jetbrains.com/lp/compose-multiplatform",
                            icon = Icons.Outlined.Code,
                        ),
                        Preference.Info(
                            title = "Stremio addon SDK",
                            subtitle = "Addon protocol — github.com/Stremio/stremio-addon-sdk",
                            icon = Icons.Outlined.Extension,
                        ),
                    ),
                ),
            )
        },
    ),
    ;
}
