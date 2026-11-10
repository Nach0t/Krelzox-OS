package dev.nexium.launcher.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkStatus {
    Wifi, Ethernet, Other, Offline;

    companion object {
        /** Pure mapping from transport flags; unit tested. */
        fun from(hasInternet: Boolean, wifi: Boolean, ethernet: Boolean): NetworkStatus = when {
            !hasInternet -> Offline
            ethernet -> Ethernet
            wifi -> Wifi
            else -> Other
        }
    }
}

interface NetworkRepository {
    fun observe(): Flow<NetworkStatus>
}

/** Default-network status via ConnectivityManager. Needs only ACCESS_NETWORK_STATE (normal permission). */
class ConnectivityNetworkRepository(context: Context) : NetworkRepository {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun observe(): Flow<NetworkStatus> = callbackFlow {
        val cm = connectivity
        if (cm == null) {
            trySend(NetworkStatus.Offline)
            awaitClose { }
            return@callbackFlow
        }
        trySend(map(cm.getNetworkCapabilities(cm.activeNetwork)))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(map(caps))
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.Offline)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun map(caps: NetworkCapabilities?): NetworkStatus = NetworkStatus.from(
        hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
    )
}
