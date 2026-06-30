package com.wavestream.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.WaveAppInit
import com.wavestream.ui.settings.Preference
import com.wavestream.ui.settings.rememberBoolPref
import com.wavestream.ui.settings.rememberFloatPref
import com.wavestream.ui.settings.rememberIntPref
import com.wavestream.ui.settings.rememberStringPref
import com.wavestream.ui.settings.widget.PreferenceScaffold
import androidx.compose.foundation.lazy.items as lazyItems
import com.lagradost.cloudstream3.plugins.RepositoryManager

/**
 * Root settings screen — a list of category cards that push into
 * sub-screens when tapped. Modeled after Anikku's `SettingsMainScreen`.
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
            CategoryRow(category = category, onClick = { onCategoryClick(category) })
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
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
private fun CategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp),
            )
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
        "General", "Default tab, auto-rescan, jsDelivr proxy", Icons.Outlined.Tune,
        buildPreferences = {
            val bootState by WaveAppInit.bootState.collectAsState()
            val autoRescan = rememberBoolPref("wavestream.auto_rescan", true)
            val defaultTab = rememberStringPref("wavestream.default_tab", "home")
            val jsdelivr = rememberBoolPref("wavestream.jsdelivr", false)
            listOf(
                Preference.Group("Startup", items = listOf(
                    Preference.List(
                        title = "Default tab",
                        subtitle = "Tab opened on app launch.",
                        icon = Icons.Outlined.Tune,
                        entries = mapOf(
                            "home" to "Home", "search" to "Search", "library" to "Library",
                            "downloads" to "Downloads", "extensions" to "Extensions",
                        ),
                        selected = defaultTab.value,
                        onSelected = { defaultTab.value = it },
                    ),
                    Preference.Switch(
                        title = "Auto-rescan extensions on boot",
                        subtitle = "Re-scan the Extensions directory every time the app starts.",
                        icon = Icons.Outlined.Restore,
                        checked = autoRescan.value,
                        onCheckedChange = { autoRescan.value = it },
                    ),
                )),
                Preference.Group("Network optimizations", items = listOf(
                    Preference.Switch(
                        title = "Use jsDelivr proxy for GitHub",
                        subtitle = "Faster downloads of CS repositories hosted on raw.githubusercontent.com.",
                        icon = Icons.Outlined.Public,
                        checked = jsdelivr.value,
                        onCheckedChange = {
                            jsdelivr.value = it
                            RepositoryManager.useJsDelivrProxy = it
                        },
                    ),
                )),
                Preference.Group("Status", items = listOf(
                    Preference.Info(
                        title = "Boot stage: ${bootState.stage}",
                        subtitle = bootState.message ?: "—",
                        icon = Icons.Outlined.Info,
                    ),
                )),
            )
        },
    ),
    APPEARANCE(
        "Appearance", "Theme, poster size, badges", Icons.Outlined.Brightness6,
        buildPreferences = {
            val themeMode = rememberStringPref("wavestream.theme_mode", "dark")
            val posterWidth = rememberIntPref("wavestream.poster_width", 110)
            val showBadges = rememberBoolPref("wavestream.show_badges", true)
            listOf(
                Preference.Group("Theme", items = listOf(
                    Preference.List(
                        title = "Theme mode",
                        subtitle = "Dark mode is recommended for media apps.",
                        icon = Icons.Outlined.Brightness6,
                        entries = mapOf("dark" to "Dark (default)", "light" to "Light", "system" to "Follow system"),
                        selected = themeMode.value,
                        onSelected = { themeMode.value = it },
                    ),
                )),
                Preference.Group("Posters", items = listOf(
                    Preference.Slider(
                        title = "Poster card width",
                        subtitle = "Width of each poster in the grid, in dp.",
                        icon = Icons.Outlined.Tune,
                        value = posterWidth.value.toFloat(),
                        valueRange = 80f..180f,
                        steps = 9,
                        onValueChange = { posterWidth.value = it.toInt() },
                    ),
                    Preference.Switch(
                        title = "Show quality badges",
                        subtitle = "Overlay resolution/quality on poster cards.",
                        icon = Icons.Outlined.Tune,
                        checked = showBadges.value,
                        onCheckedChange = { showBadges.value = it },
                    ),
                )),
            )
        },
    ),
    STREAMING(
        "Streaming", "Quality, format preference", Icons.Outlined.PlayCircle,
        buildPreferences = {
            val defaultQuality = rememberStringPref("wavestream.default_quality", "auto")
            val preferHls = rememberBoolPref("wavestream.prefer_hls", true)
            val preferStremio = rememberBoolPref("wavestream.prefer_stremio", false)
            listOf(
                Preference.Group("Quality", items = listOf(
                    Preference.List(
                        title = "Default quality",
                        subtitle = "Quality preferred when multiple streams are available.",
                        icon = Icons.Outlined.PlayCircle,
                        entries = mapOf(
                            "auto" to "Auto (best available)", "4k" to "4K UHD", "hdr" to "HDR",
                            "hd" to "HD 1080p+", "sd" to "SD 480p+",
                        ),
                        selected = defaultQuality.value,
                        onSelected = { defaultQuality.value = it },
                    ),
                    Preference.Switch(
                        title = "Prefer HLS over MP4",
                        subtitle = "HLS streams adapt to bandwidth automatically.",
                        icon = Icons.Outlined.PlayCircle,
                        checked = preferHls.value,
                        onCheckedChange = { preferHls.value = it },
                    ),
                )),
                Preference.Group("Source preference", items = listOf(
                    Preference.Switch(
                        title = "Prefer Stremio streams",
                        subtitle = "When both CS and Stremio sources are available, prefer Stremio.",
                        icon = Icons.Outlined.Extension,
                        checked = preferStremio.value,
                        onCheckedChange = { preferStremio.value = it },
                    ),
                )),
            )
        },
    ),
    PLAYER(
        "Player", "Playback speed, gestures, PiP", Icons.Outlined.PlayCircle,
        buildPreferences = {
            val speed = rememberFloatPref("wavestream.playback_speed_x10", 1.0f)
            val rememberPos = rememberBoolPref("wavestream.remember_position", true)
            val gestures = rememberBoolPref("wavestream.player_gestures", true)
            val pip = rememberBoolPref("wavestream.pip", true)
            listOf(
                Preference.Group("Playback", items = listOf(
                    Preference.Slider(
                        title = "Default playback speed",
                        subtitle = "Applied when a new video starts.",
                        icon = Icons.Outlined.PlayCircle,
                        value = speed.value,
                        valueRange = 0.25f..3.0f,
                        steps = 10,
                        onValueChange = { speed.value = it },
                    ),
                    Preference.Switch(
                        title = "Remember playback position",
                        subtitle = "Resume from where you left off across app restarts.",
                        icon = Icons.Outlined.Restore,
                        checked = rememberPos.value,
                        onCheckedChange = { rememberPos.value = it },
                    ),
                )),
                Preference.Group("Gestures", items = listOf(
                    Preference.Switch(
                        title = "Enable player gestures",
                        subtitle = "Swipe to adjust brightness, volume, and seek.",
                        icon = Icons.Outlined.Tune,
                        checked = gestures.value,
                        onCheckedChange = { gestures.value = it },
                    ),
                )),
                Preference.Group("Picture-in-Picture", items = listOf(
                    Preference.Switch(
                        title = "Enable PiP",
                        subtitle = "Mini player when navigating away from the player screen (Android).",
                        icon = Icons.Outlined.PlayCircle,
                        checked = pip.value,
                        onCheckedChange = { pip.value = it },
                    ),
                )),
            )
        },
    ),
    SUBTITLES(
        "Subtitles", "Enable, language, font size", Icons.Outlined.Subtitles,
        buildPreferences = {
            val enabled = rememberBoolPref("wavestream.subtitles_enabled", true)
            val lang = rememberStringPref("wavestream.subtitle_lang", "en")
            val fontSize = rememberFloatPref("wavestream.subtitle_size_x10", 1.6f)
            listOf(
                Preference.Group("Subtitles", items = listOf(
                    Preference.Switch(
                        title = "Enable subtitles",
                        subtitle = "Overlay subtitles on video when available.",
                        icon = Icons.Outlined.Subtitles,
                        checked = enabled.value,
                        onCheckedChange = { enabled.value = it },
                    ),
                    Preference.List(
                        title = "Subtitle language",
                        subtitle = "Preferred language for embedded and Stremio subtitles.",
                        icon = Icons.Outlined.Subtitles,
                        entries = mapOf(
                            "en" to "English", "es" to "Español", "fr" to "Français",
                            "de" to "Deutsch", "it" to "Italiano", "pt" to "Português",
                            "ru" to "Русский", "ja" to "日本語", "ko" to "한국어",
                            "zh" to "中文", "ar" to "العربية", "hi" to "हिन्दी",
                            "bn" to "বাংলা",
                        ),
                        selected = lang.value,
                        onSelected = { lang.value = it },
                    ),
                    Preference.Slider(
                        title = "Subtitle font size",
                        subtitle = "Relative scale, 1.0 = default.",
                        icon = Icons.Outlined.Tune,
                        value = fontSize.value,
                        valueRange = 0.5f..2.5f,
                        steps = 19,
                        onValueChange = { fontSize.value = it },
                    ),
                )),
            )
        },
    ),
    NETWORK(
        "Network", "Timeout, concurrent requests", Icons.Outlined.Public,
        buildPreferences = {
            val timeout = rememberIntPref("wavestream.timeout_sec", 30)
            val concurrent = rememberIntPref("wavestream.concurrent_req", 8)
            listOf(
                Preference.Group("HTTP", items = listOf(
                    Preference.Slider(
                        title = "Request timeout (seconds)",
                        subtitle = "Maximum time to wait for a provider to respond.",
                        icon = Icons.Outlined.Public,
                        value = timeout.value.toFloat(),
                        valueRange = 5f..120f,
                        steps = 22,
                        onValueChange = { timeout.value = it.toInt() },
                    ),
                    Preference.Slider(
                        title = "Concurrent requests",
                        subtitle = "Number of providers queried in parallel during search.",
                        icon = Icons.Outlined.Tune,
                        value = concurrent.value.toFloat(),
                        valueRange = 1f..32f,
                        steps = 30,
                        onValueChange = { concurrent.value = it.toInt().coerceIn(1, 32) },
                    ),
                )),
            )
        },
    ),
    ADVANCED(
        "Advanced", "Logging, restore defaults", Icons.Outlined.Code,
        buildPreferences = {
            val verbose = rememberBoolPref("wavestream.verbose_log", false)
            listOf(
                Preference.Group("Diagnostics", items = listOf(
                    Preference.Switch(
                        title = "Verbose logging",
                        subtitle = "Print every provider request and parse result to stdout.",
                        icon = Icons.Outlined.Code,
                        checked = verbose.value,
                        onCheckedChange = { verbose.value = it },
                    ),
                )),
                Preference.Group("Maintenance", items = listOf(
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
                )),
                Preference.Group("Security", items = listOf(
                    Preference.Info(
                        title = "Safe mode",
                        subtitle = if (com.lagradost.cloudstream3.plugins.PluginManager.isSafeMode())
                            "Enabled — a plugin failed to load." else "Disabled — no plugin errors detected.",
                        icon = Icons.Outlined.Security,
                    ),
                )),
            )
        },
    ),
    ABOUT(
        "About", "Version, plugins loaded, data directory", Icons.Outlined.Info,
        buildPreferences = {
            val bootState by WaveAppInit.bootState.collectAsState()
            listOf(
                Preference.Group("App", items = listOf(
                    Preference.Info("Wavestream 1.0.0", "A Compose Multiplatform fork of CloudStream 3.", Icons.Outlined.Info),
                    Preference.Info("Boot stage", bootState.stage.name, Icons.Outlined.Restore),
                    Preference.Info("Plugins loaded", "${bootState.pluginsLoaded} plugins · ${bootState.providersLoaded} providers", Icons.Outlined.Extension),
                    Preference.Info("Default repos seeded", "${bootState.reposSeeded} repos · ${bootState.addonsSeeded} Stremio addons", Icons.Outlined.Download),
                )),
                Preference.Group("Storage", items = listOf(
                    Preference.Info("Data directory", com.wavestream.platform.wavePlatform.dataDir.absolutePath, Icons.Outlined.Code),
                    Preference.Info("Extensions directory", com.wavestream.platform.wavePlatform.extensionsDir.absolutePath, Icons.Outlined.Extension),
                    Preference.Info("Downloads directory", com.wavestream.platform.wavePlatform.downloadsDir.absolutePath, Icons.Outlined.Download),
                )),
                Preference.Group("Credits", items = listOf(
                    Preference.Info("CloudStream 3", "Upstream project — github.com/recloudstream/cloudstream", Icons.Outlined.Public),
                    Preference.Info("Compose Multiplatform", "UI framework — jetbrains.com/lp/compose-multiplatform", Icons.Outlined.Code),
                    Preference.Info("Stremio addon SDK", "Addon protocol — github.com/Stremio/stremio-addon-sdk", Icons.Outlined.Extension),
                )),
            )
        },
    ),
    ;
}
