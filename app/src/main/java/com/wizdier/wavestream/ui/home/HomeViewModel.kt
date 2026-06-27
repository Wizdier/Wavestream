package com.wizdier.wavestream.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.api.HomePageList
import com.wizdier.wavestream.data.api.HomePageResponse
import com.wizdier.wavestream.data.api.Provider
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.data.plugin.PluginLoader
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
    private val historyRepo: HistoryRepository,
    private val pluginLoader: PluginLoader
) : ViewModel() {

    val continueWatching: StateFlow<List<HistoryEntity>> =
        historyRepo.observeContinueWatching()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _homeLists = MutableStateFlow<List<HomePageList>>(emptyList())
    val homeLists: StateFlow<List<HomePageList>> = _homeLists.asStateFlow()

    /** All installed providers — used to render per-provider category tabs. */
    val providers: StateFlow<List<Provider>> = pluginLoader.providers

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

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

    /** Filter home results to a specific provider's catalog. */
    fun selectProvider(providerId: String?) {
        _selectedProviderId.value = providerId
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                if (providerId == null) {
                    providerRepo.aggregateHomePage()
                } else {
                    val provider = pluginLoader.byId(providerId) ?: return@runCatching HomePageResponse(emptyList())
                    val page = provider.getMainPage(1) ?: HomePageResponse(emptyList())
                    page
                }
            }.onSuccess { _homeLists.value = it.lists }
              .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    /** Reload after a new plugin is installed. */
    fun reloadPlugins() {
        viewModelScope.launch {
            pluginLoader.reload()
            load()
        }
    }
}
