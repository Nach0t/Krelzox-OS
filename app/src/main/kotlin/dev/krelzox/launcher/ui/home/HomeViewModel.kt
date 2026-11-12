package dev.krelzox.launcher.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.krelzox.launcher.core.data.AppEntry
import dev.krelzox.launcher.core.data.AppRepository
import dev.krelzox.launcher.core.data.FavoritesRepository
import dev.krelzox.launcher.core.data.NetworkRepository
import dev.krelzox.launcher.core.data.NetworkStatus
import dev.krelzox.launcher.core.data.RecentsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Why the hero shows a given app when nothing is focused. */
enum class FeaturedReason { Continue, Recent, Favorite, Suggested }

/** Maximum number of apps the hero carousel rotates through. */
internal const val MAX_HERO_CANDIDATES = 5

/** The carousel is topped up with suggested apps until it has at least this many entries. */
internal const val MIN_HERO_CANDIDATES = 3

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
        /**
         * Hero carousel, most relevant first: last used app, other recent apps,
         * favorites. Empty only when there are no apps.
         */
        val heroCandidates: List<Featured>,
    ) : HomeUiState {
        /** Default hero content (first candidate). */
        val featured: Featured? get() = heroCandidates.firstOrNull()
    }

    data object Error : HomeUiState
}

/** Pure state assembly: keys without an installed app are hidden, never deleted. */
internal fun buildReadyState(
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
    recentKeys: List<String> = emptyList(),
    heroExcludedPackages: Set<String> = emptySet(),
): HomeUiState.Ready {
    val byKey = apps.associateBy { it.key }
    val installedFavorites = favoriteKeys.mapNotNull { byKey[it] }
    return HomeUiState.Ready(
        favorites = installedFavorites,
        apps = apps,
        favoriteKeys = installedFavorites.mapTo(LinkedHashSet()) { it.key },
        media = apps.filter { it.category.isMedia },
        heroCandidates = buildHeroCandidates(apps, byKey, installedFavorites, recentKeys, heroExcludedPackages),
    )
}

/**
 * Hero carousel: recent apps (the most recent one is "Continue"), then favorites, then
 * suggested apps from the catalog until there are [MIN_HERO_CANDIDATES], so the hero
 * stays dynamic even with few apps. Utility apps such as system Settings are excluded
 * (they have their own tab), unless nothing else is installed.
 */
private fun buildHeroCandidates(
    apps: List<AppEntry>,
    byKey: Map<String, AppEntry>,
    favorites: List<AppEntry>,
    recentKeys: List<String>,
    excluded: Set<String>,
): List<Featured> {
    val eligible = { e: AppEntry -> e.packageName !in excluded }
    val out = LinkedHashMap<String, Featured>()
    recentKeys.mapNotNull(byKey::get).filter(eligible).forEachIndexed { i, e ->
        out.putIfAbsent(e.key, Featured(e, if (i == 0) FeaturedReason.Continue else FeaturedReason.Recent))
    }
    favorites.filter(eligible).forEach { out.putIfAbsent(it.key, Featured(it, FeaturedReason.Favorite)) }
    val pool = apps.filter(eligible).ifEmpty { if (out.isEmpty()) apps else emptyList() }
    for (app in pool) {
        if (out.size >= MIN_HERO_CANDIDATES) break
        out.putIfAbsent(app.key, Featured(app, FeaturedReason.Suggested))
    }
    return out.values.take(MAX_HERO_CANDIDATES)
}

class HomeViewModel(
    appRepository: AppRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentsRepository: RecentsRepository,
    networkRepository: NetworkRepository,
    heroExcludedPackages: Set<String> = emptySet(),
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            appRepository.observeApps(),
            favoritesRepository.favorites,
            recentsRepository.recents,
        ) { apps, favorites, recents ->
            buildReadyState(apps, favorites, recents, heroExcludedPackages) as HomeUiState
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
        private const val TAG = "KrelzoxHome"

        fun factory(
            appRepository: AppRepository,
            favoritesRepository: FavoritesRepository,
            recentsRepository: RecentsRepository,
            networkRepository: NetworkRepository,
            heroExcludedPackages: Set<String>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(appRepository, favoritesRepository, recentsRepository, networkRepository, heroExcludedPackages)
            }
        }
    }
}
