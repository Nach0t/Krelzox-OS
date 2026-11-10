package dev.nexium.launcher.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.data.AppRepository
import dev.nexium.launcher.core.data.FavoritesRepository
import dev.nexium.launcher.core.data.NetworkRepository
import dev.nexium.launcher.core.data.NetworkStatus
import dev.nexium.launcher.core.data.RecentsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Why the hero shows a given app when nothing is focused. */
enum class FeaturedReason { Recent, Favorite, Suggested }

data class Featured(val entry: AppEntry, val reason: FeaturedReason)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        /** Installed favorites, in the user's order. */
        val favorites: List<AppEntry>,
        /** Every launchable app, sorted by label. */
        val apps: List<AppEntry>,
        /** Keys of installed favorites, for quick lookup. */
        val favoriteKeys: Set<String>,
        /** Apps declaring a video, audio or image category. */
        val media: List<AppEntry>,
        /** Default hero content. Null only when there are no apps. */
        val featured: Featured?,
    ) : HomeUiState

    data object Error : HomeUiState
}

/** Pure state assembly: keys without an installed app are hidden, never deleted. */
internal fun buildReadyState(
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
    recentKeys: List<String> = emptyList(),
): HomeUiState.Ready {
    val byKey = apps.associateBy { it.key }
    val installedFavorites = favoriteKeys.mapNotNull { byKey[it] }
    val featured = recentKeys.firstNotNullOfOrNull { byKey[it] }?.let { Featured(it, FeaturedReason.Recent) }
        ?: installedFavorites.firstOrNull()?.let { Featured(it, FeaturedReason.Favorite) }
        ?: apps.firstOrNull()?.let { Featured(it, FeaturedReason.Suggested) }
    return HomeUiState.Ready(
        favorites = installedFavorites,
        apps = apps,
        favoriteKeys = installedFavorites.mapTo(LinkedHashSet()) { it.key },
        media = apps.filter { it.category.isMedia },
        featured = featured,
    )
}

class HomeViewModel(
    appRepository: AppRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentsRepository: RecentsRepository,
    networkRepository: NetworkRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            appRepository.observeApps(),
            favoritesRepository.favorites,
            recentsRepository.recents,
        ) { apps, favorites, recents ->
            buildReadyState(apps, favorites, recents) as HomeUiState
        }
            .catch { e ->
                Log.e(TAG, "Failed to load apps", e)
                emit(HomeUiState.Error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    val networkStatus: StateFlow<NetworkStatus> = networkRepository.observe()
        .catch { e ->
            Log.w(TAG, "Network status unavailable", e)
            emit(NetworkStatus.Offline)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkStatus.Other)

    fun toggleFavorite(entry: AppEntry) = persist("toggle favorite ${entry.key}") {
        favoritesRepository.toggle(entry.key)
    }

    fun moveFavorite(entry: AppEntry, offset: Int) {
        val visible = (uiState.value as? HomeUiState.Ready)?.favoriteKeys ?: return
        persist("move favorite ${entry.key}") { favoritesRepository.move(entry.key, offset, visible) }
    }

    fun recordLaunch(entry: AppEntry) = persist("record launch ${entry.key}") {
        recentsRepository.recordLaunch(entry.key)
    }

    private fun persist(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to $what", e)
            }
        }
    }

    companion object {
        private const val TAG = "NexiumHome"

        fun factory(
            appRepository: AppRepository,
            favoritesRepository: FavoritesRepository,
            recentsRepository: RecentsRepository,
            networkRepository: NetworkRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appRepository, favoritesRepository, recentsRepository, networkRepository) }
        }
    }
}
