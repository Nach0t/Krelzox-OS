package dev.krelzox.remote

import android.app.Application

class RemoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteHub.init(this)
    }
}
