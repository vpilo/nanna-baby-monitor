package org.vpilo.babymonitor.network.common

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext

actual class DiscoveryManager(
    context: Context,
    coroutineContext: CoroutineContext,
) {
    private val applicationContext: Context = context.applicationContext
    private val scope = CoroutineScope(coroutineContext)

    private val nsdManager: NsdManager =
        applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val wifiManager: WifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredServers: MutableStateFlow<Set<InetAddress>> =
        MutableStateFlow(emptySet())

    actual val discoveredServers: Flow<Set<InetAddress>> =
        _discoveredServers
            .map { it.toSortedSet(compareBy { address -> address.hostAddress }) }
            .distinctUntilChanged()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    init {
        startDiscovery()
    }

    actual fun registerService() {
        scope.launch {
            val serviceInfo =
                NsdServiceInfo().apply {
                    serviceName = Constants.DISCOVERY_SERVICE_NAME
                    serviceType = Constants.DISCOVERY_SERVICE_TYPE
                    port = Constants.DISCOVERY_PORT
                }

            val listener =
                object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                        Logger.d(TAG) { "Service registered: ${serviceInfo.serviceName}" }
                    }

                    override fun onRegistrationFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        Logger.e(TAG) { "Service registration failed: errorCode=$errorCode" }
                    }

                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                        Logger.d(TAG) { "Service unregistered: ${serviceInfo.serviceName}" }
                    }

                    override fun onUnregistrationFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        Logger.e(TAG) { "Service unregistration failed: errorCode=$errorCode" }
                    }
                }
            registrationListener = listener

            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    actual fun unregisterService() {
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (ex: IllegalArgumentException) {
            Logger.e(TAG, ex) { "Failed to unregister service" }
        } finally {
            registrationListener = null
        }
    }

    actual fun startDiscovery() {
        if (discoveryListener != null) return

        scope.launch {
            acquireMulticastLock()

            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Logger.d(TAG) { "Discovery started: $serviceType" }
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        Logger.d(TAG) { "Service found: ${serviceInfo.serviceName}" }
                        resolveService(serviceInfo)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        Logger.d(TAG) { "Service lost: ${serviceInfo.serviceName}" }
                        @Suppress("DEPRECATION")
                        val host = serviceInfo.host
                        if (host != null) {
                            _discoveredServers.value -= host
                        }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Logger.d(TAG) { "Discovery stopped: $serviceType" }
                    }

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        Logger.e(TAG) { "Start discovery failed: errorCode=$errorCode" }
                        releaseMulticastLock()
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        Logger.e(TAG) { "Stop discovery failed: errorCode=$errorCode" }
                    }
                }
            discoveryListener = listener

            nsdManager.discoverServices(
                Constants.DISCOVERY_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
            releaseMulticastLock()
        }
    }

    actual fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (ex: IllegalArgumentException) {
            Logger.e(TAG, ex) { "Failed to stop discovery" }
        } finally {
            discoveryListener = null
            _discoveredServers.value = emptySet()
            releaseMulticastLock()
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        scope.launch {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        Logger.w(TAG) { "Resolve failed for ${serviceInfo.serviceName}: errorCode=$errorCode" }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = serviceInfo.host
                        if (host != null) {
                            Logger.d(TAG) { "Service resolved: ${serviceInfo.serviceName} -> $host" }
                            _discoveredServers.value += host
                        } else {
                            Logger.w(TAG) { "Service resolved but no host: ${serviceInfo.serviceName}" }
                        }
                    }
                },
            )
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val lock = wifiManager.createMulticastLock(Constants.DISCOVERY_SERVICE_NAME)
        lock.setReferenceCounted(true)
        lock.acquire()
        multicastLock = lock
        Logger.d(TAG) { "Multicast lock acquired" }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
                Logger.d(TAG) { "Multicast lock released" }
            }
        }
        multicastLock = null
    }

    private companion object {
        private val TAG = DiscoveryManager::class
    }
}
