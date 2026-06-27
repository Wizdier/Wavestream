package com.wizdier.wavestream.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * WaveStream motion specs — inspired by Material 3 motion but tuned for
 * a streaming-app feel: slightly slower entrances, snappier exits, and
 * generous spring physics on interactive elements.
 */
object Motion {

    // ── Durations ────────────────────────────────────────────────────────

    /** Quick micro-interactions: taps, hovers, chip selections. */
    const val DurationFast = 150

    /** Standard transitions: screen enters, sheet opens. */
    const val DurationMedium = 300

    /** Hero animations: poster tap → detail, player open. */
    const val DurationSlow = 450

    /** Long-running: splash, onboarding. */
    const val DurationXSlow = 600

    // ── Easings ──────────────────────────────────────────────────────────

    /** Standard ease for entrances. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Standard ease for exits. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** For interactive bounces. */
    val EmphasizedStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ── Spring specs ─────────────────────────────────────────────────────

    /** Bouncy spring for cards, chips, nav items. */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Snappy spring for buttons, switches. */
    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Slow, gentle spring for hero animations. */
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    // ── Tween helpers ────────────────────────────────────────────────────

    fun <T> fadeIn(durationMs: Int = DurationMedium) =
        tween<T>(durationMs, easing = EmphasizedDecelerate)

    fun <T> fadeOut(durationMs: Int = DurationFast) =
        tween<T>(durationMs, easing = EmphasizedAccelerate)

    fun <T> slideIn(durationMs: Int = DurationMedium) =
        tween<T>(durationMs, easing = EmphasizedDecelerate)

    fun <T> slideOut(durationMs: Int = DurationMedium) =
        tween<T>(durationMs, easing = EmphasizedAccelerate)
}

/**
 * Elevation tokens — softer than Material defaults for a more cinematic feel.
 */
object Elevation {
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp     // cards at rest
    val Level2: Dp = 3.dp     // cards on hover
    val Level3: Dp = 6.dp     // sheets, dialogs
    val Level4: Dp = 8.dp     // FAB, bottom nav
    val Level5: Dp = 12.dp    // pull-out menus
}

/**
 * Corner radius tokens — larger than Material defaults for friendliness.
 */
object Corners {
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 20.dp
    val XXLarge: Dp = 28.dp
    val Pill: Dp = 999.dp
}

/**
 * Spacing tokens — 4pt grid.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}
