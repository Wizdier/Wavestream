package com.wizdier.wavestream.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.api.CatalogType
import com.wizdier.wavestream.data.api.SearchFilter
import com.wizdier.wavestream.data.api.SearchResponse
import com.wizdier.wavestream.data.db.dao.SearchHistoryDao
import com.wizdier.wavestream.data.db.entities.SearchHistoryEntity
import com.wizdier.wavestream.data.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val providerRepo: ProviderRepository,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    val recentSearches: StateFlow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResponse>>(emptyList())
    val results: StateFlow<List<SearchResponse>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _filterOpen = MutableStateFlow(false)
    val filterOpen: StateFlow<Boolean> = _filterOpen.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    fun toggleFilterSheet() { _filterOpen.value = !_filterOpen.value }

    fun updateFilter(transform: (SearchFilter) -> SearchFilter) {
        _filter.value = transform(_filter.value)
    }

    fun toggleType(type: CatalogType) = updateFilter { f ->
        f.copy(types = if (type in f.types) f.types - type else f.types + type)
    }

    fun clearFilters() { _filter.value = SearchFilter() }

    fun runSearch() {
        val q = _query.value.trim()
        if (q.isEmpty()) { _results.value = emptyList(); return }
        viewModelScope.launch {
            _isSearching.value = true
            runCatching {
                searchHistoryDao.insert(SearchHistoryEntity(query = q))
                providerRepo.search(q, _filter.value)
            }.onSuccess { _results.value = it }
              .onFailure { _results.value = emptyList() }
            _isSearching.value = false
        }
    }

    fun removeRecent(query: String) {
        viewModelScope.launch { searchHistoryDao.remove(query) }
    }
}
