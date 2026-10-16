package org.vpilo.babymonitor.network.internal.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import kotlin.coroutines.CoroutineContext

internal actual class LocalDiscoveryDataSource(
    context: Context,
    private val coroutineContext: CoroutineContext,
) {
    actual constructor(coroutineContext: CoroutineContext) : this(
        context = KoinPlatform.getKoin().get(),
        coroutineContext = coroutineContext,
    )

    private val applicationContext: Context = context.applicationContext

    private val nsdManager: NsdManager =
        applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val multicastLock: MulticastLock = MulticastLock(applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)

    private var discoveryListener: AndroidDiscoveryListener? = null

    actual fun startDiscovery(
        device: Device,
        discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
    ) {
        check(discoveryListener == null) { "Discovery already started" }
        discoveredDevices.update { emptyMap() }
        discoveryListener =
            AndroidDiscoveryListener(
                coroutineContext = coroutineContext,
                localId = device.id,
                nsdManager = nsdManager,
                discoveredDevices = discoveredDevices,
            )
        multicastLock.acquire(device.idString)
        nsdManager.discoverServices(
            DISCOVERY_ANDROID_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )
    }

    actual fun stopDiscovery() {
        checkNotNull(discoveryListener) { "Discovery not started" }
        nsdManager.stopServiceDiscovery(discoveryListener)
        multicastLock.release()
        // It's apparently unreliable to keep using the same listener between sessions, so make a new one every time.
        discoveryListener?.release()
        discoveryListener = null
    }

    companion object {
        const val TAG = "LocalDiscoveryDataSource"
    }
}
