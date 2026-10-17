package org.vpilo.babymonitor.network.internal.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.internal.discovery.ktx.toAttributes
import org.vpilo.babymonitor.network.model.Constants
import kotlin.coroutines.CoroutineContext

internal actual class LocalDiscoveryServerDataSource(
    context: Context,
    coroutineContext: CoroutineContext,
) {
    actual constructor(
        coroutineContext: CoroutineContext,
    ) : this(
        context = KoinPlatform.getKoin().get(),
        coroutineContext = coroutineContext,
    )

    private val applicationContext: Context = context.applicationContext

    private val nsdManager: NsdManager =
        applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val multicastLock: MulticastLock = MulticastLock(applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)

    private var registrationListener: AndroidRegistrationListener? = null

    actual fun registerService(device: Device.Server) {
        multicastLock.acquire(device.idString)
        registrationListener = AndroidRegistrationListener()
        nsdManager.registerService(createServiceInfo(device), NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    actual fun unregisterService() {
        nsdManager.unregisterService(registrationListener)
        registrationListener = null
        multicastLock.release()
    }

    companion object {
        const val TAG = "LocalDiscoveryServerDataSource"

        private fun createServiceInfo(device: Device): NsdServiceInfo =
            NsdServiceInfo().apply {
                serviceName = device.idString
                serviceType = DISCOVERY_ANDROID_SERVICE_TYPE
                port = Constants.SERVICE_PORT
                device.toAttributes().forEach { (name, value) ->
                    setAttribute(name, value)
                }
            }
    }
}
