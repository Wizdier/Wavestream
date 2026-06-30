package com.wavestream.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.wavestream.platform.wavePlatform

/**
 * Reactive preference helpers. Each function returns a [MutableState] that:
 *  - Reads its initial value from [wavePlatform.preferences] on first composition
 *  - Writes back to preferences whenever the state changes
 *  - Triggers Compose recomposition on every change
 *
 * Usage:
 * ```
 * val jsdelivr by rememberBoolPref("wavestream.jsdelivr", false)
 * Switch(checked = jsdelivr, onCheckedChange = { jsdelivr = it })
 * ```
 *
 * The state is scoped to the composition — if the same key is used in two
 * different composables, they each get their own state. To share state
 * across composables, hoist it to a parent or use a shared StateFlow.
 */
@Composable
fun rememberBoolPref(key: String, default: Boolean): MutableState<Boolean> {
    val prefs = wavePlatform.preferences
    val state = remember(key) { mutableStateOf(prefs.getBool(key, default)) }
    return remember(key) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(newValue) {
                    if (state.value != newValue) {
                        state.value = newValue
                        prefs.putBool(key, newValue)
                    }
                }
            override fun component1(): Boolean = state.value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberStringPref(key: String, default: String): MutableState<String> {
    val prefs = wavePlatform.preferences
    val state = remember(key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    return remember(key) {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(newValue) {
                    if (state.value != newValue) {
                        state.value = newValue
                        prefs.putString(key, newValue)
                    }
                }
            override fun component1(): String = state.value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberIntPref(key: String, default: Int): MutableState<Int> {
    val prefs = wavePlatform.preferences
    val state = remember(key) { mutableIntStateOf(prefs.getInt(key, default)) }
    return remember(key) {
        object : MutableState<Int> {
            override var value: Int
                get() = state.value
                set(newValue) {
                    if (state.value != newValue) {
                        state.value = newValue
                        prefs.putInt(key, newValue)
                    }
                }
            override fun component1(): Int = state.value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberFloatPref(key: String, default: Float): MutableState<Float> {
    val prefs = wavePlatform.preferences
    val state = remember(key) { mutableFloatStateOf(default) }
    // Float prefs are stored as Int (x10) because WavePreferences doesn't
    // have a native float accessor — we scale on read/write.
    val scaledDefault = (default * 10).toInt()
    val initialScaled = prefs.getInt(key, scaledDefault)
    val initialFloat = initialScaled / 10f
    return remember(key) {
        object : MutableState<Float> {
            private var _value = initialFloat
            override var value: Float
                get() = _value
                set(newValue) {
                    if (_value != newValue) {
                        _value = newValue
                        prefs.putInt(key, (newValue * 10).toInt())
                    }
                }
            override fun component1(): Float = _value
            override fun component2(): (Float) -> Unit = { value = it }
        }
    }
}
