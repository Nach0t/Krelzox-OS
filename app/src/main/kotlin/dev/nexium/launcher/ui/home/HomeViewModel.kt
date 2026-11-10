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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        /** Installed favorites, in the user's order. */
        val favorites: List<AppEntry>,
        /** Every launchable app, sorted by label. */
        val apps: List<AppEntry>,
        /** Keys of installed favorites, for quick lookup. */
        val favoriteKeys: Set<String>,
    ) : HomeUiState

    data object Error : HomeUiState
}

/** Pure state assembly: favorites without an installed app are hidden, not deleted. */
internal fun buildReadyState(apps: List<AppEntry>, favoriteKeys: List<String>): HomeUiState.Ready {
    val byKey = apps.associateBy { it.key }
    val installedFavorites = favoriteKeys.mapNotNull { byKey[it] }
    return HomeUiState.Ready(
        favorites = installedFavorites,
        apps = apps,
        favoriteKeys = installedFavorites.mapTo(LinkedHashSet()) { it.key },
    )
}

class HomeViewModel(
    appRepository: AppRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(appRepository.observeApps(), favoritesRepository.favorites) { apps, favorites ->
            buildReadyState(apps, favorites) as HomeUiState
        }
            .catch { e ->
                Log.e(TAG, "Failed to load apps", e)
                emit(HomeUiState.Error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun toggleFavorite(entry: AppEntry) {
        viewModelScope.launch {
            try {
                favoritesRepository.toggle(entry.key)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to update favorite ${entry.key}", e)
            }
        }
    }

    companion object {
        private const val TAG = "NexiumHome"

        fun factory(
            appRepository: AppRepository,
            favoritesRepository: FavoritesRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appRepository, favoritesRepository) }
        }
    }
}
