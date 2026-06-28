package com.wizdier.wavestream.ui.settings.repos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.db.entities.RepoEntity
import com.wizdier.wavestream.data.plugin.ExtensionInstaller
import com.wizdier.wavestream.data.plugin.PluginLoader
import com.wizdier.wavestream.data.repository.RepoExtension
import com.wizdier.wavestream.data.repository.RepoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RepoSettingsViewModel(private val repo: RepoRepository, private val installer: ExtensionInstaller, private val pluginLoader: PluginLoader) : ViewModel() {
    val repos: StateFlow<List<RepoEntity>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _extensions = MutableStateFlow<Map<Long, List<RepoExtension>>>(emptyMap())
    val extensions: StateFlow<Map<Long, List<RepoExtension>>> = _extensions.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()
    private val _installing = MutableStateFlow<Set<String>>(emptySet())
    val installing: StateFlow<Set<String>> = _installing.asStateFlow()

    fun add(url: String) { viewModelScope.launch { _adding.value = true; runCatching { repo.add(url.trim()) }.onSuccess { _error.value = null; refresh(it.rowId) }.onFailure { _error.value = friendlyError(it) }; _adding.value = false } }
    fun refresh(rowId: Long) { viewModelScope.launch { runCatching { repo.refresh(rowId) }.onSuccess { _extensions.value = _extensions.value + (rowId to it) }.onFailure { _error.value = friendlyError(it) } } }
    fun remove(rowId: Long) { viewModelScope.launch { runCatching { repo.remove(rowId) }.onSuccess { _extensions.value = _extensions.value - rowId }.onFailure { _error.value = friendlyError(it) } } }

    fun install(extension: RepoExtension) {
        viewModelScope.launch {
            _installing.value = _installing.value + extension.apk
            runCatching { installer.install(extension) { pluginLoader.reload() } }
                .onSuccess { if (extension.apk.endsWith(".cs3", true)) _error.value = "Installed ${extension.name} — restart app to activate" }
                .onFailure { _error.value = "Install failed: ${it.message}" }
            _installing.value = _installing.value - extension.apk
        }
    }

    private fun friendlyError(t: Throwable): String {
        val msg = t.message ?: t::class.simpleName ?: "Unknown error"
        return when {
            msg.contains("Unable to resolve host", true) -> "Can't reach that URL — check your internet."
            msg.contains("Failed to parse", true) -> "That URL didn't return valid JSON."
            msg.contains("Got HTML", true) -> "Got HTML, not JSON. Use the raw repo.json URL."
            msg.contains("HTTP 4", true) -> "Server rejected ($msg)."
            msg.contains("already added", true) -> "Already in your list."
            else -> msg
        }
    }
}
