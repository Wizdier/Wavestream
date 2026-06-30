package com.wavestream.ui.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavestream.ui.settings.Preference

/**
 * Scaffold that renders a list of [Preference]s as a Material 3 settings
 * screen. Groups get a section header; dividers separate items within a
 * group; groups are separated by a larger spacer.
 *
 * Modeled after Anikku's `PreferenceScaffold` — single lazy column,
 * Material 3 surface rows, no nested scroll containers.
 */
@Composable
fun PreferenceScaffold(
    preferences: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        preferences.forEach { pref ->
            when (pref) {
                is Preference.Group -> {
                    item(key = "group-header-${pref.title}") {
                        Text(
                            text = pref.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    lazyItems(pref.items, key = { item -> "item-${pref.title}-${item.hashCode()}" }) { item ->
                        PreferenceItemWidget(preference = item)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item(key = "group-spacer-${pref.title}") {
                        Spacer(Modifier.height(16.dp))
                    }
                }
                else -> {
                    item(key = "top-level-${pref.hashCode()}") {
                        PreferenceItemWidget(preference = pref)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
