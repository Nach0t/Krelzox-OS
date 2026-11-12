package dev.krelzox.launcher

import android.app.Application
import android.content.Context
import dev.krelzox.launcher.core.data.AppRepository
import dev.krelzox.launcher.core.data.ConnectivityNetworkRepository
import dev.krelzox.launcher.core.data.DataStoreFavoritesRepository
import dev.krelzox.launcher.core.data.FavoritesRepository
import dev.krelzox.launcher.core.data.NetworkDetails
import dev.krelzox.launcher.core.data.NetworkRepository
import dev.krelzox.launcher.core.data.PackageManagerAppRepository
import dev.krelzox.launcher.core.data.RecentsRepository
import dev.krelzox.launcher.core.data.metrics.SystemMetrics
import dev.krelzox.launcher.core.data.metrics.SystemMonitor
import dev.krelzox.launcher.safety.CrashGuard
import dev.krelzox.launcher.ui.AppArtLoader
import dev.krelzox.launcher.ui.appearance.AppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

class KrelzoxApp : Application() {
    /** Manual DI container (see docs/arquitectura.md). */
    val container: AppContainer by lazy { AppContainer(this) }

    lateinit var crashGuard: CrashGuard
        private set

    /** Theme selection, shared by the launcher and the screensaver. */
    val appearance: AppearanceStore by lazy { AppearanceStore(this) }

    override fun onCreate() {
        super.onCreate()
        crashGuard = CrashGuard(this).also { it.install() }
        // Before any UI (launcher or screensaver) draws its first frame.
        appearance.restore()
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

    // System metrics: ONE sampler shared by the top-bar panel, the expanded view and the
    // performance center. It runs only while some screen observes it (collection is
    // lifecycle-aware), and stops 3 s after the last observer leaves.
    private val metricsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val systemMetrics: SharedFlow<SystemMetrics> = SystemMonitor(context).metrics
        .shareIn(metricsScope, SharingStarted.WhileSubscribed(3_000), replay = 1)
    val networkDetails: SharedFlow<NetworkDetails> = networkRepository.observeDetails()
        .shareIn(metricsScope, SharingStarted.WhileSubscribed(3_000), replay = 1)
}
