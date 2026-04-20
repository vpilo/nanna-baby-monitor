package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.io.IOException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ServerId
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

actual class DiscoveryManager {
    private val discoveryService = JmDNS.create(InetAddress.getByName(Constants.SERVICES_LISTEN_ADDRESS))

    private val remoteServiceListener = RemoteServiceListener(::isLocalDeviceHost)

    private var deviceName = ""

    actual val discoveredServers: Flow<Set<DiscoveredServer>> =
        remoteServiceListener.discoveredServers
            .map { it.toSortedSet() }
            .distinctUntilChanged()

    private val _state = MutableStateFlow(DiscoveryManagerState.Idle)
    actual val state: Flow<DiscoveryManagerState> = _state.asStateFlow()

    init {
        startDiscovery()
    }

    actual fun registerService() {
        if (_state.value == DiscoveryManagerState.DiscoveringServices) {
            stopDiscovery()
        }
        check(deviceName.isNotEmpty()) { "Device name must be set before registering service!" }
        Logger.d(TAG) { "Service registered: $SERVICE_TYPE ($deviceName) on ${Constants.SERVICES_LISTEN_ADDRESS}" }
        try {
            discoveryService.registerService(createServiceInfo(deviceName))
            _state.value = DiscoveryManagerState.ServiceRegistered
        } catch (ex: IOException) {
            Logger.e(TAG, ex) { "Failed to register service: $SERVICE_TYPE" }
        }
    }

    actual fun unregisterService() {
        Logger.d(TAG) { "Service unregistered: $SERVICE_TYPE" }
        discoveryService.unregisterAllServices()
        _state.value = DiscoveryManagerState.Idle
    }

    actual fun startDiscovery() {
        if (_state.value == DiscoveryManagerState.ServiceRegistered) {
            unregisterService()
        }
        Logger.d(TAG) { "Discovering services: $SERVICE_TYPE" }
        discoveryService.addServiceListener(SERVICE_TYPE, remoteServiceListener)
        _state.value = DiscoveryManagerState.DiscoveringServices
    }

    actual fun stopDiscovery() {
        Logger.d(TAG) { "Stopped discovering services: $SERVICE_TYPE" }
        discoveryService.removeServiceListener(SERVICE_TYPE, remoteServiceListener)
        remoteServiceListener.reset()
        _state.value = DiscoveryManagerState.Idle
    }

    actual fun setDeviceName(name: String) {
        deviceName = name
        if (_state.value == DiscoveryManagerState.ServiceRegistered) {
            unregisterService()
            registerService()
        }
    }

    private class RemoteServiceListener(
        private val isLocalDeviceHost: (Set<InetAddress>) -> Boolean,
    ) : ServiceListener {
        private val _discoveredServers: MutableStateFlow<Set<DiscoveredServer>> = MutableStateFlow(emptySet())

        val discoveredServers: StateFlow<Set<DiscoveredServer>> = _discoveredServers.asStateFlow()

        fun reset() {
            _discoveredServers.value = emptySet()
        }

        override fun serviceAdded(event: ServiceEvent) {
            if (isLocalDeviceHost(event.hosts)) return
            Logger.d(TAG) { "Service added: ${event.info.name} at hosts ${event.hosts}" }
            event.dns.requestServiceInfo(event.type, event.name)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            val hosts = event.hosts
            Logger.d(TAG) { "Service lost: ${event.info.name} at hosts $hosts" }
            _discoveredServers.value
                .firstOrNull { it.matchesAddresses(hosts) }
                ?.let { device ->
                    _discoveredServers.value -= device
                }
                ?: run {
                    _discoveredServers.value
                        .firstOrNull { it.id.name == event.info.name }
                        ?.let { device -> _discoveredServers.value -= device }
                }
        }

        override fun serviceResolved(event: ServiceEvent) {
            val name = event.info.name
            val hosts = event.hosts

            if (isLocalDeviceHost(hosts)) return

            if (hosts.isEmpty()) {
                Logger.w(TAG) { "Service resolved with no hosts: $name" }
                return
            }

            Logger.i(TAG) { "Service resolved: $name -> $hosts" }
            _discoveredServers.update { servers ->
                val new = DiscoveredServer(ServerId(name), hosts)
                servers
                    .firstOrNull { it.matchesAddresses(hosts) }
                    ?.let { old -> servers + new - old }
                    ?: run { servers + new }
            }
        }

        private val ServiceEvent.hosts: Set<InetAddress>
            get() = (info.inet6Addresses?.toSet() ?: emptySet()) + (info.inet4Addresses?.toSet() ?: emptySet())
    }

    private fun isLocalDeviceHost(addresses: Set<InetAddress>): Boolean =
        addresses.intersect(discoveryService.inetAddress?.let { setOf(it) } ?: emptySet()).isNotEmpty()

    private companion object {
        // JmDNS requires the ".local." suffix
        private const val SERVICE_TYPE = "${Constants.DISCOVERY_SERVICE_TYPE}local."

        private fun createServiceInfo(deviceName: String): ServiceInfo =
            ServiceInfo.create(
                SERVICE_TYPE,
                deviceName,
                Constants.DISCOVERY_PORT,
                Constants.DISCOVERY_SERVICE_DESCRIPTION,
            )

        private val TAG = DiscoveryManager::class
    }
}
