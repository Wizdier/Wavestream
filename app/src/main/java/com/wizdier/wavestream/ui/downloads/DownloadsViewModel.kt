package com.wizdier.wavestream.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.db.entities.DownloadEntity
import com.wizdier.wavestream.data.repository.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val repo: DownloadRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(rowId: Long) = viewModelScope.launch { repo.setStatus(rowId, "paused") }
    fun resume(rowId: Long) = viewModelScope.launch { repo.setStatus(rowId, "queued") }
    fun cancel(rowId: Long) = viewModelScope.launch { repo.delete(rowId) }
}
