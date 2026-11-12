package dev.krelzox.store.data

import dev.krelzox.store.domain.DeviceProfile
import dev.krelzox.store.domain.InstalledPackage
import dev.krelzox.store.model.Catalog
import dev.krelzox.store.model.RepoConfig
import dev.krelzox.store.model.RepoIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sync status of one repository, shown in Settings. */
data class RepoStatus(
    val repo: RepoConfig,
    val appCount: Int,
    val checkedAt: Long,
    /** Null when the last refresh worked; otherwise "network", "verification" or "format". */
    val error: String?,
)

data class CatalogState(
    val catalog: Catalog = Catalog.EMPTY,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val repos: List<RepoStatus> = emptyList(),
)

/**
 * Holds the merged catalog and the installed packages. On start it shows the cached,
 * re-verified indexes immediately and refreshes them from the network when they are
 * older than [MAX_AGE_MS] (or on demand).
 */
class CatalogRepository(
    private val client: RepoClient,
    private val preferences: StorePreferences,
    private val deviceApps: DeviceApps,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val _installed = MutableStateFlow<Map<String, InstalledPackage>>(emptyMap())
    val installed: StateFlow<Map<String, InstalledPackage>> = _installed.asStateFlow()

    val device: DeviceProfile by lazy { deviceApps.profile() }

    private var refreshJob: Job? = null

    fun start() {
        refreshInstalled()
        scope.launch {
            val repos = enabledRepos()
            val cached = repos.mapNotNull { repo -> client.loadCached(repo)?.let { repo to it } }
            publish(cached.map { it.second }, repos.map { repo ->
                val index = cached.firstOrNull { it.first.id == repo.id }?.second
                RepoStatus(repo, index?.apps?.size ?: 0, preferences.syncState(repo.id).checkedAt, null)
            }, loading = cached.isEmpty())
            val stale = repos.any { System.currentTimeMillis() - preferences.syncState(it.id).checkedAt > MAX_AGE_MS }
            if (cached.size < repos.size || stale) refresh()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            _state.update { it.copy(refreshing = true) }
            val repos = enabledRepos()
            val results = repos.map { client.refresh(it) }
            val indexes = ArrayList<RepoIndex>()
            val statuses = results.zip(repos).map { (result, repo) ->
                val checkedAt = preferences.syncState(repo.id).checkedAt
                when (result) {
                    is RepoClient.Result.Ok -> {
                        indexes += result.index
                        RepoStatus(repo, result.index.apps.size, checkedAt, null)
                    }
                    is RepoClient.Result.Failed -> {
                        result.cached?.let(indexes::add)
                        RepoStatus(repo, result.cached?.apps?.size ?: 0, checkedAt, result.reason)
                    }
                }
            }
            publish(indexes, statuses, loading = false)
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun refreshInstalled() {
        scope.launch {
            _installed.value = withContext(Dispatchers.IO) { deviceApps.installed() }
        }
    }

    private suspend fun enabledRepos(): List<RepoConfig> = preferences.repos.first().filter { it.enabled }

    private suspend fun publish(indexes: List<RepoIndex>, statuses: List<RepoStatus>, loading: Boolean) {
        val catalog = withContext(Dispatchers.Default) { Catalog.merge(indexes) }
        _state.update { it.copy(catalog = catalog, repos = statuses, loading = loading && catalog.apps.isEmpty()) }
    }

    private companion object {
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    }
}
