package org.vpilo.babymonitor.data.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL

internal actual class DeviceStateDataSource(
    private val context: Context,
) {
    actual constructor() : this(context = KoinPlatform.getKoin().get())

    actual val batteryLevel: Flow<Int> =
        flow {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            while (true) {
                val batteryStatus: Intent? = context.registerReceiver(null, filter)
                if (batteryStatus == null) {
                    emit(DEVICE_STATE_DATA_UNAVAILABLE)
                } else {
                    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, DEVICE_STATE_DATA_UNAVAILABLE)

                    emit(level.coerceIn(0, 100))
                }
                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }

    actual val signalLevel: Flow<Int> =
        flow {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            while (true) {
                emit(
                    if (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                        DEVICE_STATE_DATA_UNAVAILABLE
                    } else {
                        val quality = getWifiSignalQuality(wifiManager)
                        quality
                    },
                )
                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }

    actual val isInternetAvailable: Flow<Boolean> =
        callbackFlow {
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

    @Suppress("DEPRECATION")
    private fun getWifiSignalQuality(wifiManager: WifiManager): Int {
        val rssi = wifiManager.connectionInfo.rssi
        if (rssi == UNKNOWN_RSSI) return DEVICE_STATE_DATA_UNAVAILABLE
        return WifiManager.calculateSignalLevel(rssi, 101).coerceIn(0, 100)
    }

    private companion object {
        private const val UNKNOWN_RSSI = -127
    }
}
