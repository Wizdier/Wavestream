package com.wizdier.wavestream.ui.settings.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.db.entities.RepoEntity
import com.wizdier.wavestream.data.repository.RepoExtension
import com.wizdier.wavestream.data.repository.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RepoSettingsViewModel(
    private val repo: RepoRepository
) : ViewModel() {

    val repos: StateFlow<List<RepoEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extensions = MutableStateFlow<Map<Long, List<RepoExtension>>>(emptyMap())
    val extensions: StateFlow<Map<Long, List<RepoExtension>>> = _extensions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun add(url: String) {
        viewModelScope.launch {
            _adding.value = true
            runCatching { repo.add(url.trim()) }
                .onSuccess { entity ->
                    _error.value = null
                    // Eagerly fetch extensions so the user sees the installable
                    // providers immediately after adding the repo.
                    refresh(entity.rowId)
                }
                .onFailure { _error.value = friendlyError(it) }
            _adding.value = false
        }
    }

    fun refresh(rowId: Long) {
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { repo.refresh(rowId) }
                .onSuccess { exts -> _extensions.value = _extensions.value + (rowId to exts) }
                .onFailure { _error.value = friendlyError(it) }
            _refreshing.value = false
        }
    }

    fun remove(rowId: Long) {
        viewModelScope.launch {
            runCatching { repo.remove(rowId) }
                .onSuccess {
                    // Drop the cached extensions for the removed repo.
                    _extensions.value = _extensions.value - rowId
                }
                .onFailure { _error.value = friendlyError(it) }
        }
    }

    private fun friendlyError(t: Throwable): String {
        val msg = t.message ?: t::class.simpleName ?: "Unknown error"
        return when {
            msg.contains("Unable to resolve host", true) ||
                msg.contains("UnknownHost", true) ->
                "Couldn't reach that URL — check your internet connection."
            msg.contains("Failed to parse", true) ->
                "That URL didn't return valid JSON. Make sure it points to a raw repo.json file."
            msg.contains("Got HTML", true) ->
                "That URL returned a web page, not a JSON file. Use the raw URL (e.g. raw.githubusercontent.com/...)."
            msg.contains("HTTP 4", true) ->
                "The server rejected the request ($msg). The repo URL may be wrong or private."
            msg.contains("HTTP 5", true) ->
                "The repo server had an error ($msg). Try again later."
            msg.contains("already added", true) ->
                "That repository is already in your list."
            else -> msg
        }
    }
}
