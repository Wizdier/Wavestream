package com.wizdier.wavestream.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.db.entities.FavoriteEntity
import com.wizdier.wavestream.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val repo: FavoritesRepository
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listNames: StateFlow<List<String>> =
        repo.observeListNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(itemId: String, listName: String) = viewModelScope.launch { repo.remove(itemId, listName) }
}
