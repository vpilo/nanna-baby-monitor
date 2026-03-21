package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.common.Logger
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.coroutines.CoroutineContext

class DiscoveryManager(
    private val coroutineContext: CoroutineContext
) {
    private val discoveryService = JmDNS.create(Constants.SERVICES_LISTEN_ADDRESS)

    private val remoteServiceListener = RemoteServiceListener()

    val discoveredServers: Flow<Set<InetAddress>> = remoteServiceListener.discoveredServers
        .map { it.toSortedSet { a, b -> a.hostAddress.compareTo(b.hostAddress) } }
        .distinctUntilChanged()

    init {
        startDiscovery()
    }

    fun registerService() {
        Logger.d(TAG) { "Service registered: $SERVICE_TYPE on ${Constants.SERVICES_LISTEN_ADDRESS}" }
        try {
            discoveryService.registerService(createServiceInfo())
        } catch (ex: Exception) {
            Logger.e(TAG, ex) { "Failed to register service: $SERVICE_TYPE" }
        }
    }

    fun unregisterService() {
        Logger.d(TAG) { "Service unregistered: $SERVICE_TYPE" }
        try {
            discoveryService.unregisterAllServices()
        } catch (ex: Exception) {
            Logger.e(TAG, ex) { "Failed to unregister service: $SERVICE_TYPE" }
        }
    }

    fun startDiscovery() {
        Logger.d(TAG) { "discovering services: $SERVICE_TYPE" }
        try {
            discoveryService.addServiceListener(SERVICE_TYPE, remoteServiceListener)
        } catch (ex: Exception) {
            Logger.e(TAG, ex) { "Failed discovering services: $SERVICE_TYPE" }
        }
    }

    fun stopDiscovery() {
        Logger.d(TAG) { "stopped discovering services: $SERVICE_TYPE" }
        try {
            discoveryService.removeServiceListener(SERVICE_TYPE, remoteServiceListener)
            remoteServiceListener.reset()
        } catch (ex: Exception) {
            Logger.e(TAG, ex) { "Failed stopped discovering services: $SERVICE_TYPE" }
        }
    }

    private class RemoteServiceListener : ServiceListener {
        private val _discoveredServers: MutableStateFlow<Set<InetAddress>> = MutableStateFlow(emptySet())

        val discoveredServers: StateFlow<Set<InetAddress>> = _discoveredServers.asStateFlow()

        fun reset() {
            _discoveredServers.value = emptySet()
        }

        override fun serviceAdded(event: ServiceEvent) {
            event.dns.requestServiceInfo(event.type, event.name)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            val addresses = event.info.inetAddresses.filterNotNull()
            Logger.d(TAG) { "Service removed: ${event.info} -> $addresses" }
            _discoveredServers.value -= addresses.toSet()
        }

        override fun serviceResolved(event: ServiceEvent) {
            val address = bestAddress(event.info)
            if (address != null) {
                Logger.d(TAG) { "Service resolved: ${event.info} -> $address" }
                _discoveredServers.value += address
            } else {
                Logger.w(TAG) { "Service resolved but no usable address: ${event.info}" }
            }
        }

        // In LANs, IPv4 is more likely to be in use.
        private fun bestAddress(info: ServiceInfo): InetAddress? =
            info.inet4Addresses.firstOrNull()
                ?: info.inet6Addresses.firstOrNull()
    }

    private companion object {
        private const val SERVICE_TYPE = "_babymonitor._tcp.local."

        private fun createServiceInfo(): ServiceInfo =
            ServiceInfo.create(
                SERVICE_TYPE,
                "BabyMonitor",
                Constants.DISCOVERY_PORT,
                "Baby Monitor service",
            )

        private val TAG = DiscoveryManager::class
    }
}
