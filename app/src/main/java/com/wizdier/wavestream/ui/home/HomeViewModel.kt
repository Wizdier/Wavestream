package com.wizdier.wavestream.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.api.HomePageList
import com.wizdier.wavestream.data.api.HomePageResponse
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.data.repository.HistoryRepository
import com.wizdier.wavestream.data.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val providerRepo: ProviderRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {

    val continueWatching: StateFlow<List<HistoryEntity>> =
        historyRepo.observeContinueWatching()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _homeLists = MutableStateFlow<List<HomePageList>>(emptyList())
    val homeLists: StateFlow<List<HomePageList>> = _homeLists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                providerRepo.initialize()
                providerRepo.aggregateHomePage()
            }.onSuccess { response: HomePageResponse ->
                _homeLists.value = response.lists
            }.onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }
}
