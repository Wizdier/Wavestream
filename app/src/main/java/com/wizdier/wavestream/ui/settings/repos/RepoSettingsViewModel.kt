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

    fun add(url: String) {
        viewModelScope.launch {
            _adding.value = true
            runCatching { repo.add(url.trim()) }
                .onSuccess { entity ->
                    _error.value = null
                    // Eagerly load the extensions list so the user sees the
                    // installable providers immediately after adding the repo.
                    refresh(entity.rowId)
                }
                .onFailure { _error.value = it.message ?: "Failed to add repository" }
            _adding.value = false
        }
    }

    fun refresh(rowId: Long) {
        viewModelScope.launch {
            runCatching { repo.refresh(rowId) }
                .onSuccess { exts -> _extensions.value = _extensions.value + (rowId to exts) }
                .onFailure { _error.value = it.message ?: "Failed to refresh repository" }
        }
    }

    fun remove(rowId: Long) {
        viewModelScope.launch {
            runCatching { repo.remove(rowId) }
                .onSuccess {
                    // Drop the cached extensions for the removed repo.
                    _extensions.value = _extensions.value - rowId
                }
        }
    }
}
