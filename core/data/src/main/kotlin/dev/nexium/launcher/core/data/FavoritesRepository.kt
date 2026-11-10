package dev.nexium.launcher.core.data

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
}

// Single DataStore instance per process, as required by DataStore.
// A corrupted file is replaced with empty preferences instead of crashing the launcher.
private val Context.nexiumPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "nexium_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Favorites persisted with Preferences DataStore.
 *
 * Favorites of uninstalled apps are intentionally NOT pruned here: during an app
 * update the package is briefly unavailable and pruning would silently lose the
 * favorite. The UI layer filters keys that have no installed app.
 */
class DataStoreFavoritesRepository(context: Context) : FavoritesRepository {

    private val dataStore = context.applicationContext.nexiumPreferences

    override val favorites: Flow<List<String>> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "Failed to read favorites; showing none", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { FavoritesCodec.decode(it[FAVORITES]) }
        .distinctUntilChanged()

    override suspend fun toggle(key: String) {
        dataStore.edit { prefs ->
            val updated = FavoritesCodec.toggle(FavoritesCodec.decode(prefs[FAVORITES]), key)
            prefs[FAVORITES] = FavoritesCodec.encode(updated)
        }
    }

    private companion object {
        const val TAG = "NexiumFavorites"
        val FAVORITES = stringPreferencesKey("favorites")
    }
}
