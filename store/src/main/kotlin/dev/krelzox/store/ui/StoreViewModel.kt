package dev.krelzox.store.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.krelzox.store.StoreApp
import dev.krelzox.store.data.HistoryEntry
import dev.krelzox.store.domain.AppStatus
import dev.krelzox.store.domain.AppStatusResolver
import dev.krelzox.store.domain.Search
import dev.krelzox.store.model.CatalogApp
import dev.krelzox.store.model.RepoConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level sections of the navigation rail, in order. */
enum class Section { SEARCH, HOME, APPS, GAMES, CATEGORIES, LIBRARY, INSTALLED, UPDATES, FAVORITES, SETTINGS }

/** Pages opened on top of a section; Back closes them one by one. */
sealed interface Route {
    data class App(val packageName: String) : Route
    data class Category(val name: String) : Route
}

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StoreApp
    private val catalogRepo = app.catalog
    private val installs = app.installs
    private val preferences = app.preferences

    val images: ImageLoader = app.images
    val catalog = catalogRepo.state
    val installed = catalogRepo.installed
    val tasks = installs.tasks
    val device get() = catalogRepo.device

    val favorites: StateFlow<Set<String>> =
        preferences.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val repos: StateFlow<List<RepoConfig>> =
        preferences.repos.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _section = MutableStateFlow(Section.HOME)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    val searchResults: StateFlow<List<CatalogApp>> = combine(catalog, _query) { state, query ->
        Search.search(state.catalog.apps, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val updates: StateFlow<List<Pair<CatalogApp, AppStatus.Updatable>>> = combine(catalog, installed) { state, installedNow ->
        AppStatusResolver.updates(state.catalog.apps, device, installedNow)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Catalog apps that are installed on the device, by name. */
    val installedApps: StateFlow<List<CatalogApp>> = combine(catalog, installed) { state, installedNow ->
        state.catalog.apps.filter { it.packageName in installedNow }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteApps: StateFlow<List<CatalogApp>> = combine(catalog, favorites) { state, favs ->
        favs.mapNotNull { state.catalog.byPackage[it] }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _canInstall = MutableStateFlow(checkCanInstall())

    /** Whether the user allowed Krelzox Store as an install source (re-checked on resume). */
    val canInstall: StateFlow<Boolean> = _canInstall.asStateFlow()

    private fun checkCanInstall() = getApplication<Application>().packageManager.canRequestPackageInstalls()

    init {
        viewModelScope.launch {
            preferences.lastSection()?.let { saved ->
                Section.entries.firstOrNull { it.name == saved && it != Section.SEARCH }?.let { _section.value = it }
            }
        }
        // Reload the history whenever an operation finishes (its task disappears).
        viewModelScope.launch {
            tasks.map { it.keys }.collect { reloadHistory() }
        }
    }

    fun select(section: Section) {
        _section.value = section
        _routes.value = emptyList()
        if (section == Section.LIBRARY) reloadHistory()
        viewModelScope.launch { preferences.setLastSection(section.name) }
    }

    fun open(route: Route) = _routes.update { it + route }

    /** Closes the top page; returns false when there was none (Back then leaves the store). */
    fun back(): Boolean {
        if (_routes.value.isEmpty()) return false
        _routes.update { it.dropLast(1) }
        return true
    }

    fun setQuery(text: String) {
        _query.value = text.take(80)
    }

    fun status(app: CatalogApp): AppStatus =
        AppStatusResolver.resolve(app, device, installed.value[app.packageName])

    fun install(app: CatalogApp) {
        val target = when (val status = status(app)) {
            is AppStatus.Installable -> status.version
            is AppStatus.Updatable -> status.version
            else -> return
        }
        val repo = repos.value.firstOrNull { it.id == app.repoId } ?: return
        installs.install(app, target, repo.address)
    }

    fun updateAll() = updates.value.forEach { (app, _) -> install(app) }

    fun uninstall(app: CatalogApp) = installs.uninstall(app)

    fun cancel(packageName: String) = installs.cancel(packageName)

    fun dismissFailure(packageName: String) = installs.dismissFailure(packageName)

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch { preferences.toggleFavorite(packageName) }
    }

    fun refreshCatalog() = catalogRepo.refresh()

    fun onResume() {
        _canInstall.value = checkCanInstall()
        catalogRepo.refreshInstalled()
    }

    fun setRepoEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = preferences.repos.first()
            preferences.saveRepos(current.map { if (it.id == id) it.copy(enabled = enabled) else it })
            catalogRepo.refresh()
        }
    }

    /** Adds a repository after validating it; returns false when the input is invalid. */
    fun addRepo(name: String, address: String, fingerprint: String): Boolean {
        val validAddress = RepoConfig.normalizeAddress(address) ?: return false
        val validFingerprint = RepoConfig.normalizeFingerprint(fingerprint) ?: return false
        viewModelScope.launch {
            val current = preferences.repos.first()
            if (current.any { it.address == validAddress }) return@launch
            val id = "repo" + System.currentTimeMillis()
            preferences.saveRepos(current + RepoConfig(id, name.trim().ifBlank { validAddress }, validAddress, validFingerprint))
            catalogRepo.refresh()
        }
        return true
    }

    private fun reloadHistory() {
        viewModelScope.launch { _history.value = app.history.read() }
    }

    fun launchIntent(packageName: String) = app.deviceApps.launchIntent(packageName)
}
