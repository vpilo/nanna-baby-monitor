package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import org.vpilo.babymonitor.common.Logger
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

actual class DiscoveryManager {
    private val discoveryService = JmDNS.create(Constants.SERVICES_LISTEN_ADDRESS)

    private val remoteServiceListener = RemoteServiceListener()

    actual val discoveredServers: Flow<Set<InetAddress>> =
        remoteServiceListener.discoveredServers
            .map { it.toSortedSet { a, b -> a.hostAddress.compareTo(b.hostAddress) } }
            .distinctUntilChanged()

    init {
        startDiscovery()
    }

    actual fun registerService() {
        Logger.d(TAG) { "Service registered: $SERVICE_TYPE on ${Constants.SERVICES_LISTEN_ADDRESS}" }
        try {
            discoveryService.registerService(createServiceInfo())
        } catch (ex: IOException) {
            Logger.e(TAG, ex) { "Failed to register service: $SERVICE_TYPE" }
        }
    }

    actual fun unregisterService() {
        Logger.d(TAG) { "Service unregistered: $SERVICE_TYPE" }
        discoveryService.unregisterAllServices()
    }

    actual fun startDiscovery() {
        Logger.d(TAG) { "discovering services: $SERVICE_TYPE" }
        discoveryService.addServiceListener(SERVICE_TYPE, remoteServiceListener)
    }

    actual fun stopDiscovery() {
        Logger.d(TAG) { "stopped discovering services: $SERVICE_TYPE" }
        discoveryService.removeServiceListener(SERVICE_TYPE, remoteServiceListener)
        remoteServiceListener.reset()
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
        // JmDNS requires the ".local." suffix
        private const val SERVICE_TYPE = "${Constants.DISCOVERY_SERVICE_TYPE}local."

        private fun createServiceInfo(): ServiceInfo =
            ServiceInfo.create(
                SERVICE_TYPE,
                Constants.DISCOVERY_SERVICE_NAME,
                Constants.DISCOVERY_PORT,
                Constants.DISCOVERY_SERVICE_DESCRIPTION,
            )

        private val TAG = DiscoveryManager::class
    }
}
