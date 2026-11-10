package dev.nexium.launcher

import android.app.Application
import android.content.Context
import dev.nexium.launcher.core.data.AppRepository
import dev.nexium.launcher.core.data.ConnectivityNetworkRepository
import dev.nexium.launcher.core.data.DataStoreFavoritesRepository
import dev.nexium.launcher.core.data.FavoritesRepository
import dev.nexium.launcher.core.data.NetworkDetails
import dev.nexium.launcher.core.data.NetworkRepository
import dev.nexium.launcher.core.data.PackageManagerAppRepository
import dev.nexium.launcher.core.data.RecentsRepository
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import dev.nexium.launcher.core.data.metrics.SystemMonitor
import dev.nexium.launcher.safety.CrashGuard
import dev.nexium.launcher.ui.AppArtLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

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

    // System metrics: ONE sampler shared by the top-bar panel, the expanded view and the
    // performance center. It runs only while some screen observes it (collection is
    // lifecycle-aware), and stops 3 s after the last observer leaves.
    private val metricsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val systemMetrics: SharedFlow<SystemMetrics> = SystemMonitor(context).metrics
        .shareIn(metricsScope, SharingStarted.WhileSubscribed(3_000), replay = 1)
    val networkDetails: SharedFlow<NetworkDetails> = networkRepository.observeDetails()
        .shareIn(metricsScope, SharingStarted.WhileSubscribed(3_000), replay = 1)
}
