package com.wizdier.wavestream.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.data.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repo: HistoryRepository
) : ViewModel() {

    val history: StateFlow<List<HistoryEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() = viewModelScope.launch { repo.clear() }

    fun remove(rowId: Long) = viewModelScope.launch { repo.delete(rowId) }
}
