package dev.krelzox.launcher.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
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

/** Connection details for the performance center. Fields are null when Android does not report them. */
data class NetworkDetails(
    val status: NetworkStatus,
    /** Android validated real Internet access on this network. */
    val validated: Boolean,
    val interfaceName: String?,
    val ipv4: String?,
    val ipv6: String?,
    /** Negotiated Wi-Fi link rate reported by the driver (not measured throughput). */
    val wifiLinkMbps: Int?,
) {
    companion object {
        val Offline = NetworkDetails(NetworkStatus.Offline, false, null, null, null, null)

        /** Picks the addresses to show: first IPv4, and a global (non link-local) IPv6. Pure. */
        fun pickAddresses(addresses: List<java.net.InetAddress>): Pair<String?, String?> {
            val v4 = addresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
            val v6 = addresses.firstOrNull { it is java.net.Inet6Address && !it.isLinkLocalAddress }
                ?.hostAddress?.substringBefore('%')
            return v4 to v6
        }
    }
}

interface NetworkRepository {
    fun observe(): Flow<NetworkStatus>

    /** Default network details, updated on capability and address changes. */
    fun observeDetails(): Flow<NetworkDetails>
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

    override fun observeDetails(): Flow<NetworkDetails> = callbackFlow {
        val cm = connectivity
        if (cm == null) {
            trySend(NetworkDetails.Offline)
            awaitClose { }
            return@callbackFlow
        }
        var caps: NetworkCapabilities? = cm.getNetworkCapabilities(cm.activeNetwork)
        var link: LinkProperties? = cm.getLinkProperties(cm.activeNetwork)
        fun publish() {
            if (caps == null) {
                trySend(NetworkDetails.Offline)
                return
            }
            val (v4, v6) = NetworkDetails.pickAddresses(link?.linkAddresses.orEmpty().map { it.address })
            trySend(
                NetworkDetails(
                    status = map(caps),
                    validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                    interfaceName = link?.interfaceName,
                    ipv4 = v4,
                    ipv6 = v6,
                    wifiLinkMbps = (caps?.transportInfo as? WifiInfo)?.linkSpeed?.takeIf { it > 0 },
                ),
            )
        }
        publish()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                caps = networkCapabilities
                publish()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                link = linkProperties
                publish()
            }

            override fun onLost(network: Network) {
                caps = null
                link = null
                publish()
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
