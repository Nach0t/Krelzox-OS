package dev.krelzox.launcher.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

interface FavoritesRepository {
    /** Ordered app keys ([AppEntry.key]) marked as favorite. */
    val favorites: Flow<List<String>>

    /** Adds the key if absent (at the end), removes it otherwise. */
    suspend fun toggle(key: String)

    /**
     * Moves a favorite by [offset] positions (negative = towards the start), counted
     * among the [visible] (installed) favorites only.
     */
    suspend fun move(key: String, offset: Int, visible: Set<String>)
}

interface RecentsRepository {
    /** App keys in most-recently-launched-first order. */
    val recents: Flow<List<String>>

    suspend fun recordLaunch(key: String)
}

// Single DataStore instance per process, as required by DataStore.
// A corrupted file is replaced with empty preferences instead of crashing the launcher.
private val Context.krelzoxPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "krelzox_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Favorites and launch history persisted with Preferences DataStore.
 *
 * Keys of uninstalled apps are intentionally NOT pruned here: during an app
 * update the package is briefly unavailable and pruning would silently lose the
 * entry. The UI layer filters keys that have no installed app.
 */
class DataStoreFavoritesRepository(context: Context) : FavoritesRepository, RecentsRepository {

    private val dataStore = context.applicationContext.krelzoxPreferences

    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) {
            Log.w(TAG, "Failed to read preferences; using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val favorites: Flow<List<String>> = safeData
        .map { FavoritesCodec.decode(it[FAVORITES]) }
        .distinctUntilChanged()

    override val recents: Flow<List<String>> = safeData
        .map { FavoritesCodec.decode(it[RECENTS]) }
        .distinctUntilChanged()

    override suspend fun toggle(key: String) = update(FAVORITES) { FavoritesCodec.toggle(it, key) }

    override suspend fun move(key: String, offset: Int, visible: Set<String>) =
        update(FAVORITES) { FavoritesCodec.moveAmongVisible(it, key, offset, visible) }

    override suspend fun recordLaunch(key: String) =
        update(RECENTS) { FavoritesCodec.pushRecent(it, key, MAX_RECENTS) }

    private suspend fun update(key: Preferences.Key<String>, transform: (List<String>) -> List<String>) {
        dataStore.edit { prefs ->
            prefs[key] = FavoritesCodec.encode(transform(FavoritesCodec.decode(prefs[key])))
        }
    }

    private companion object {
        const val TAG = "KrelzoxPreferences"
        const val MAX_RECENTS = 8
        val FAVORITES = stringPreferencesKey("favorites")
        val RECENTS = stringPreferencesKey("recents")
    }
}
