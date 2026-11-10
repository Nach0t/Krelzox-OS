package dev.nexium.launcher

import android.app.Application
import android.content.Context
import dev.nexium.launcher.core.data.AppRepository
import dev.nexium.launcher.core.data.ConnectivityNetworkRepository
import dev.nexium.launcher.core.data.DataStoreFavoritesRepository
import dev.nexium.launcher.core.data.FavoritesRepository
import dev.nexium.launcher.core.data.NetworkRepository
import dev.nexium.launcher.core.data.PackageManagerAppRepository
import dev.nexium.launcher.core.data.RecentsRepository
import dev.nexium.launcher.safety.CrashGuard
import dev.nexium.launcher.ui.AppArtLoader

class NexiumApp : Application() {
    /** Manual DI container (see docs/arquitectura.md). */
    val container: AppContainer by lazy { AppContainer(this) }

    lateinit var crashGuard: CrashGuard
        private set

    override fun onCreate() {
        super.onCreate()
        crashGuard = CrashGuard(this).also { it.install() }
    }
}

class AppContainer(context: Context) {
    val appRepository: AppRepository = PackageManagerAppRepository(context)

    // One DataStore-backed store serves favorites and launch history.
    private val preferences = DataStoreFavoritesRepository(context)
    val favoritesRepository: FavoritesRepository = preferences
    val recentsRepository: RecentsRepository = preferences

    val networkRepository: NetworkRepository = ConnectivityNetworkRepository(context)
    val artLoader: AppArtLoader = AppArtLoader(context)
}
