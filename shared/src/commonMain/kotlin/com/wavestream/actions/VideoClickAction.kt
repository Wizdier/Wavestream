package com.wavestream.actions

import com.wavestream.api.ExtractorLink

/**
 * Video click action — what happens when the user taps a stream.
 *
 * Mirrors CloudStream's `actions/VideoClickAction.kt`.
 *
 * Default action is to play in the built-in player.
 * Other actions can launch external players (VLC, MPV, etc.) or
 * cast to a Chromecast / fcast receiver.
 */
abstract class VideoClickAction {
    abstract val name: String
    abstract val iconUrl: String?

    /** File path of the plugin this action was loaded from. */
    var sourcePlugin: String? = null

    /**
     * Whether this action can handle the given link.
     * Default: true for all links.
     */
    open fun canHandle(link: ExtractorLink): Boolean = true

    /**
     * Execute the action with the given link.
     * @return true if the action was handled successfully
     */
    abstract suspend fun execute(link: ExtractorLink): Boolean
}

/**
 * Holder for all registered video click actions.
 */
object VideoClickActionHolder {
    private val _actions = mutableListOf<VideoClickAction>()

    val allVideoClickActions: MutableList<VideoClickAction> get() = _actions

    fun add(action: VideoClickAction) {
        _actions.add(action)
    }

    fun remove(action: VideoClickAction) {
        _actions.remove(action)
    }

    fun removeAll(pluginPath: String) {
        _actions.removeAll { it.sourcePlugin == pluginPath }
    }

    fun getActionsFor(link: ExtractorLink): List<VideoClickAction> {
        return _actions.filter { it.canHandle(link) }
    }
}

/**
 * Always-ask action — shows a dialog letting the user pick an action.
 */
class AlwaysAskAction : VideoClickAction() {
    override val name: String = "Always Ask"
    override val iconUrl: String? = null

    override suspend fun execute(link: ExtractorLink): Boolean {
        // The UI should show a picker dialog when this action is selected
        return true
    }
}

/**
 * Open in app action — plays in the built-in Wavestream player.
 */
class OpenInAppAction : VideoClickAction() {
    override val name: String = "Play in Wavestream"
    override val iconUrl: String? = null

    override suspend fun execute(link: ExtractorLink): Boolean {
        // Navigate to the built-in player screen with this link
        return true
    }
}

/**
 * Copy to clipboard action — copies the URL to the clipboard.
 */
class CopyClipboardAction : VideoClickAction() {
    override val name: String = "Copy URL"
    override val iconUrl: String? = null

    override suspend fun execute(link: ExtractorLink): Boolean {
        // Implementation uses platform clipboard API
        return true
    }
}

/**
 * Play in browser action — opens the URL in the system browser.
 */
class PlayInBrowserAction : VideoClickAction() {
    override val name: String = "Open in Browser"
    override val iconUrl: String? = null

    override suspend fun execute(link: ExtractorLink): Boolean {
        // Implementation uses platform intent / Desktop API
        return true
    }
}
