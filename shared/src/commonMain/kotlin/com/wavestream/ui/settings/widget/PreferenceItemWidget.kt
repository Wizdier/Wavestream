package com.wavestream.ui.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavestream.ui.settings.Preference

/**
 * Renders a single [Preference] as a Material 3 row. The shape matches
 * Anikku's `TextPreferenceWidget`: an optional leading icon, title +
 * optional subtitle on the left, and an optional trailing control on
 * the right.
 */
@Composable
fun PreferenceItemWidget(
    preference: Preference,
    modifier: Modifier = Modifier,
) {
    when (preference) {
        is Preference.Text -> TextPreferenceWidget(
            title = preference.title,
            subtitle = preference.subtitle,
            icon = preference.icon,
            enabled = preference.enabled,
            onClick = preference.onClick,
            modifier = modifier,
        )
        is Preference.Switch -> SwitchPreferenceWidget(
            title = preference.title,
            subtitle = preference.subtitle,
            icon = preference.icon,
            checked = preference.checked,
            enabled = preference.enabled,
            onCheckedChange = preference.onCheckedChange,
            modifier = modifier,
        )
        is Preference.List -> ListPreferenceWidget(
            title = preference.title,
            subtitle = preference.subtitle,
            icon = preference.icon,
            entries = preference.entries,
            selected = preference.selected,
            onSelected = preference.onSelected,
            modifier = modifier,
        )
        is Preference.Slider -> SliderPreferenceWidget(
            title = preference.title,
            subtitle = preference.subtitle,
            icon = preference.icon,
            value = preference.value,
            valueRange = preference.valueRange,
            steps = preference.steps,
            onValueChange = preference.onValueChange,
            modifier = modifier,
        )
        is Preference.Info -> InfoPreferenceWidget(
            title = preference.title,
            subtitle = preference.subtitle,
            icon = preference.icon,
            modifier = modifier,
        )
        is Preference.Group -> error("Group should be rendered by PreferenceScaffold, not PreferenceItemWidget")
    }
}

@Composable
private fun TextPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val clickMod = if (onClick != null && enabled) {
        Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth()
    } else {
        Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth()
    }
    Surface(
        onClick = { if (enabled) onClick?.invoke() },
        enabled = enabled && onClick != null,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = clickMod,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun ListPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    entries: Map<String, String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val currentLabel = entries[selected] ?: selected

    Surface(
        onClick = { showPicker = true },
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = subtitle ?: currentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (value, label) ->
                        Surface(
                            onClick = {
                                onSelected(value)
                                showPicker = false
                            },
                            color = if (value == selected)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (value == selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SliderPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun InfoPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
