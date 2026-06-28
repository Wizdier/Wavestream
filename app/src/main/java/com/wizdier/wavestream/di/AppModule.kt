package com.wizdier.wavestream.di
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.wizdier.wavestream.data.db.WaveStreamDatabase
import com.wizdier.wavestream.data.db.dao.*
import com.wizdier.wavestream.data.plugin.ExtensionInstaller
import com.wizdier.wavestream.data.plugin.PluginLoader
import com.wizdier.wavestream.data.repository.*
import com.wizdier.wavestream.data.sync.MalSync
import com.wizdier.wavestream.data.sync.TraktSync
import com.wizdier.wavestream.ui.detail.DetailViewModel
import com.wizdier.wavestream.ui.downloads.DownloadsViewModel
import com.wizdier.wavestream.ui.favorites.FavoritesViewModel
import com.wizdier.wavestream.ui.history.HistoryViewModel
import com.wizdier.wavestream.ui.home.HomeViewModel
import com.wizdier.wavestream.ui.player.PlayerViewModel
import com.wizdier.wavestream.ui.search.SearchViewModel
import com.wizdier.wavestream.ui.settings.repos.RepoSettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val Context.dataStore by preferencesDataStore(name = "wavestream_prefs")

val appModule = module {
    single { Room.databaseBuilder(androidContext(), WaveStreamDatabase::class.java, WaveStreamDatabase.NAME).fallbackToDestructiveMigration().build() }
    single<HistoryDao> { get<WaveStreamDatabase>().historyDao() }
    single<FavoritesDao> { get<WaveStreamDatabase>().favoritesDao() }
    single<DownloadDao> { get<WaveStreamDatabase>().downloadDao() }
    single<RepoDao> { get<WaveStreamDatabase>().repoDao() }
    single<SearchHistoryDao> { get<WaveStreamDatabase>().searchHistoryDao() }
    single { PluginLoader(androidContext()) }
    single { ExtensionInstaller(androidContext()) }
    single { ProviderRepository(get()) }
    single { HistoryRepository(get()) }
    single { FavoritesRepository(get()) }
    single { DownloadRepository(get()) }
    single { RepoRepository(get()) }
    single { SubtitleRepository(androidContext().cacheDir) }
    single { TraktSync(clientId = androidContext().getString(com.wizdier.wavestream.R.string.trakt_client_id), clientSecret = androidContext().getString(com.wizdier.wavestream.R.string.trakt_client_secret)) }
    single { MalSync(clientId = androidContext().getString(com.wizdier.wavestream.R.string.mal_client_id)) }
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::DetailViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::RepoSettingsViewModel)
}
