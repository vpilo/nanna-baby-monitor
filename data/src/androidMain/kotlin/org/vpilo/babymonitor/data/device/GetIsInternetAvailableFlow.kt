package org.vpilo.babymonitor.data.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.mp.KoinPlatform

internal actual fun getIsInternetAvailableFlow(): Flow<Boolean> =
    callbackFlow {
        val context: Context = KoinPlatform.getKoin().get()
        val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

        val lock = Any()
        val knownNetworks = mutableMapOf<Network, Set<String>>()
        var previousAddresses: Set<String> = emptySet()

        fun recompute() {
            synchronized(lock) {
                val addresses = knownNetworks.values.flatten().toSet()
                if (addresses != previousAddresses) {
                    trySend(addresses.isNotEmpty())
                    previousAddresses = addresses
                }
            }
        }

        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties,
                ) {
                    synchronized(lock) {
                        knownNetworks[network] =
                            linkProperties.linkAddresses
                                .mapNotNull { it.address.hostAddress }
                                .toSet()
                    }
                    recompute()
                }

                override fun onLost(network: Network) {
                    synchronized(lock) { knownNetworks.remove(network) }
                    recompute()
                }
            }

        synchronized(lock) {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.forEach { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@forEach
                val linkProperties = connectivityManager.getLinkProperties(network) ?: return@forEach
                knownNetworks[network] =
                    linkProperties.linkAddresses
                        .mapNotNull { it.address.hostAddress }
                        .toSet()
            }
            previousAddresses = knownNetworks.values.flatten().toSet()
            trySend(previousAddresses.isNotEmpty())
        }
        connectivityManager.registerNetworkCallback(networkRequest, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
