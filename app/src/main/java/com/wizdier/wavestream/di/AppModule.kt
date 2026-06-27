package com.wizdier.wavestream.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.wizdier.wavestream.data.db.WaveStreamDatabase
import com.wizdier.wavestream.data.db.dao.DownloadDao
import com.wizdier.wavestream.data.db.dao.FavoritesDao
import com.wizdier.wavestream.data.db.dao.HistoryDao
import com.wizdier.wavestream.data.db.dao.RepoDao
import com.wizdier.wavestream.data.db.dao.SearchHistoryDao
import com.wizdier.wavestream.data.plugin.ExtensionInstaller
import com.wizdier.wavestream.data.plugin.PluginLoader
import com.wizdier.wavestream.data.repository.DownloadRepository
import com.wizdier.wavestream.data.repository.FavoritesRepository
import com.wizdier.wavestream.data.repository.HistoryRepository
import com.wizdier.wavestream.data.repository.ProviderRepository
import com.wizdier.wavestream.data.repository.RepoRepository
import com.wizdier.wavestream.data.repository.SubtitleRepository
import com.wizdier.wavestream.data.sync.MalSync
import com.wizdier.wavestream.data.sync.TraktSync
import com.wizdier.wavestream.ui.detail.DetailViewModel
import com.wizdier.wavestream.ui.downloads.DownloadsViewModel
import com.wizdier.wavestream.ui.favorites.FavoritesViewModel
import com.wizdier.wavestream.ui.history.HistoryViewModel
import com.wizdier.wavestream.ui.home.HomeViewModel
import com.wizdier.wavestream.ui.player.PlayerViewModel
import com.wizdier.wavestream.ui.search.SearchViewModel
import com.wizdier.wavestream.ui.settings.SettingsViewModel
import com.wizdier.wavestream.ui.settings.repos.RepoSettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val Context.dataStore by preferencesDataStore(name = "wavestream_prefs")

val appModule = module {
    // Database
    single {
        Room.databaseBuilder(androidContext(), WaveStreamDatabase::class.java, WaveStreamDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
    single<HistoryDao> { get<WaveStreamDatabase>().historyDao() }
    single<FavoritesDao> { get<WaveStreamDatabase>().favoritesDao() }
    single<DownloadDao> { get<WaveStreamDatabase>().downloadDao() }
    single<RepoDao> { get<WaveStreamDatabase>().repoDao() }
    single<SearchHistoryDao> { get<WaveStreamDatabase>().searchHistoryDao() }

    // Plugin loader + extension installer
    single { PluginLoader(androidContext()) }
    single { ExtensionInstaller(androidContext()) }

    // Settings persistence (DataStore-backed)
    single { com.wizdier.wavestream.data.settings.SettingsRepository(androidContext()) }

    // Backup / restore
    single { com.wizdier.wavestream.data.backup.BackupManager(androidContext(), get(), get()) }

    // Repositories
    single { ProviderRepository(get()) }
    single { HistoryRepository(get()) }
    single { FavoritesRepository(get()) }
    single { DownloadRepository(get()) }
    single { RepoRepository(get()) }
    single { SubtitleRepository(androidContext().cacheDir) }

    // Sync clients — replace these placeholder credentials with your own
    // before publishing; users can override in Settings > Sync.
    single {
        TraktSync(
            clientId = androidContext().getString(com.wizdier.wavestream.R.string.trakt_client_id),
            clientSecret = androidContext().getString(com.wizdier.wavestream.R.string.trakt_client_secret)
        )
    }
    single { MalSync(clientId = androidContext().getString(com.wizdier.wavestream.R.string.mal_client_id)) }

    // ViewModels — Koin resolves constructor params from the module above.
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::DetailViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::RepoSettingsViewModel)
    viewModelOf(::SettingsViewModel)
}
