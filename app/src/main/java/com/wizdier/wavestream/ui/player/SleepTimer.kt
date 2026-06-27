package com.wizdier.wavestream.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Sleep timer — pauses the player after a chosen duration.
 *  - 15 min / 30 min / 1 hour / 2 hours / End of current episode
 *  - Shows remaining time in a snackbar when active
 *  - Cancels on user tap
 */
class SleepTimer {
    var endTimeMs: Long = 0L
        private set

    fun start(durationMinutes: Int) {
        endTimeMs = System.currentTimeMillis() + durationMinutes * 60_000L
    }

    fun startEndOfEpisode(remainingMs: Long) {
        endTimeMs = System.currentTimeMillis() + remainingMs
    }

    fun cancel() {
        endTimeMs = 0L
    }

    val isActive: Boolean get() = endTimeMs > 0L

    val remainingMs: Long
        get() = if (isActive) (endTimeMs - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
}

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val options = listOf(
        15 to "15 minutes",
        30 to "30 minutes",
        60 to "1 hour",
        120 to "2 hours",
        -1 to "End of current episode"
    )
    var selected by remember { mutableStateOf(15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == minutes,
                                onClick = { selected = minutes }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == minutes,
                            onClick = { selected = minutes }
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected == -1) onSet(-1) else onSet(selected)
                onDismiss()
            }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = {
                onCancel()
                onDismiss()
            }) { Text("Cancel timer") }
        }
    )
}
