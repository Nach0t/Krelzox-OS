package dev.nexium.launcher

import android.app.Application
import android.content.Context
import dev.nexium.launcher.core.data.AppRepository
import dev.nexium.launcher.core.data.PackageManagerAppRepository
import dev.nexium.launcher.ui.AppArtLoader

class NexiumApp : Application() {
    /** Manual DI container (see docs/arquitectura.md). */
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val appRepository: AppRepository = PackageManagerAppRepository(context)
    val artLoader: AppArtLoader = AppArtLoader(context)
}
