package com.wavestream.features.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Layout type — mirrors CloudStream's `Globals` layout system.
 *
 * CloudStream detects TV vs Phone vs Emulator at startup and inflates
 * different layouts. In Compose we use CompositionLocals to provide
 * layout-specific values.
 */
enum class AppLayout {
    PHONE, TV, EMULATOR;

    val isTv: Boolean get() = this == TV
    val isEmulator: Boolean get() = this == EMULATOR
    val isPhone: Boolean get() = this == PHONE
    val isLandscape: Boolean get() = this == TV || this == EMULATOR
}

/**
 * CompositionLocal that provides the current app layout.
 */
val LocalAppLayout = staticCompositionLocalOf { AppLayout.PHONE }

/**
 * Detect the layout based on device characteristics.
 *
 * On Android: checks UiModeManager for UI_MODE_TYPE_TELEVISION,
 * Build.MODEL for "AFT" (Fire TV), "firestick", "fire tv", "chromecast".
 *
 * On Desktop: always PHONE layout (can be overridden in settings).
 */
expect fun detectLayout(): AppLayout

/**
 * Helper to check the current layout inside a composable.
 */
@Composable
fun isLayout(vararg layouts: AppLayout): Boolean {
    val current = LocalAppLayout.current
    return current in layouts
}

/**
 * TV focus handling utilities.
 *
 * On TV, the user navigates with a D-pad. We need to:
 *   - Highlight the currently-focused item
 *   - Auto-scroll to keep focused items visible
 *   - Handle D-pad events (up/down/left/right/enter/back)
 *
 * CloudStream implements this with a custom focus overlay.
 * In Compose for TV, we can use `androidx.tv.foundation` and
 * `androidx.tv.material3` which provide built-in focus handling.
 */
object TvFocus {
    /**
     * Whether the animated focus outline is enabled (TV only).
     */
    var animatedOutline: Boolean = true
}
