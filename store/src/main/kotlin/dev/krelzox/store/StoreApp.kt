package dev.krelzox.store

import android.app.Application
import android.content.res.Resources
import dev.krelzox.store.data.CatalogRepository
import dev.krelzox.store.data.DeviceApps
import dev.krelzox.store.data.HistoryStore
import dev.krelzox.store.data.RepoClient
import dev.krelzox.store.data.StorePreferences
import dev.krelzox.store.index.IndexParser
import dev.krelzox.store.install.InstallManager
import dev.krelzox.store.install.SystemInstaller
import dev.krelzox.store.ui.ImageLoader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application graph: one instance of each store component, shared by the UI. */
class StoreApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var preferences: StorePreferences
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var deviceApps: DeviceApps
        private set
    lateinit var catalog: CatalogRepository
        private set
    lateinit var installs: InstallManager
        private set
    lateinit var images: ImageLoader
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = StorePreferences(this)
        history = HistoryStore(this)
        deviceApps = DeviceApps(this)
        val locales = Resources.getSystem().configuration.locales
        val client = RepoClient(
            cacheDir = filesDir,
            preferences = preferences,
            locales = IndexParser.preferredLocales((0 until locales.size()).map { locales[it] }),
        )
        catalog = CatalogRepository(client, preferences, deviceApps, scope)
        installs = InstallManager(
            apkDir = File(cacheDir, "apks"),
            installer = SystemInstaller(this),
            deviceApps = deviceApps,
            history = history,
            scope = scope,
            onPackagesChanged = catalog::refreshInstalled,
        )
        images = ImageLoader(File(cacheDir, "images"))
        // Leftovers from an interrupted download are never installed: remove them.
        File(cacheDir, "apks").listFiles()?.forEach { it.delete() }
        catalog.start()
    }
}
