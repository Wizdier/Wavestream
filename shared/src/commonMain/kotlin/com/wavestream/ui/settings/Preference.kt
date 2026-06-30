package com.wavestream.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Preference DSL modeled after Anikku's `Preference`-based settings framework.
 *
 * Each [Preference] is one row in a settings screen. [PreferenceGroup] lets
 * you group rows under a section title. The DSL is intentionally declarative
 * so screens can be built as pure data — easy to test, easy to extend.
 *
 * The renderer ([PreferenceScaffold] / [PreferenceItemWidget]) lives in
 * `ui/settings/widget/` and turns these data classes into Material 3
 * components.
 */
sealed interface Preference {
    /** A row with an optional icon, title, subtitle, and tap action. */
    data class Text(
        val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
        val onClick: (() -> Unit)? = null,
    ) : Preference

    /** A row with a switch at the trailing edge. */
    data class Switch(
        val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : Preference

    /** A row showing the current value; tapping opens a picker dialog. */
    data class List(
        val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val entries: Map<String, String>, // value -> human label
        val selected: String,
        val onSelected: (String) -> Unit,
    ) : Preference

    /** A row with a slider at the trailing edge. */
    data class Slider(
        val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val value: Float,
        val valueRange: ClosedFloatingPointRange<Float>,
        val steps: Int = 0,
        val onValueChange: (Float) -> Unit,
    ) : Preference

    /** A section header with a title and a list of preferences under it. */
    data class Group(
        val title: String,
        val items: kotlin.collections.List<Preference>,
    ) : Preference

    /** An informational banner (no interaction). */
    data class Info(
        val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
    ) : Preference
}

/**
 * Convenience builder so screens can write `group(title = "...") { ... }`
 * instead of `Preference.Group(title = "...", items = listOf(...))`.
 */
fun group(title: String, vararg items: Preference): Preference.Group =
    Preference.Group(title = title, items = items.toList())
