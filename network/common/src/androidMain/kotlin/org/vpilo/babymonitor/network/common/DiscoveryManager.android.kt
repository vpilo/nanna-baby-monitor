package org.vpilo.babymonitor.network.common

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ServerId
import java.net.InetAddress
import java.net.SocketException
import kotlin.coroutines.CoroutineContext

actual class DiscoveryManager(
    context: Context,
    coroutineContext: CoroutineContext,
) {
    private val applicationContext: Context = context.applicationContext
    private val scope = CoroutineScope(coroutineContext)

    private var deviceName = ""

    private val nsdManager: NsdManager =
        applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val wifiManager: WifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredServers: MutableStateFlow<Set<Server>> =
        MutableStateFlow(emptySet())

    actual val discoveredServersFlow: Flow<Set<Server>> =
        _discoveredServers
            .map { it.toSortedSet() }
            .distinctUntilChanged()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val _state = MutableStateFlow(DiscoveryManagerState.Idle)
    actual val state: Flow<DiscoveryManagerState> = _state.asStateFlow()

    private var runningJob: Job? = null

    actual fun registerService() {
        check(deviceName.isNotEmpty()) { "Device name must be set before registering service!" }
        if (_state.value == DiscoveryManagerState.DiscoveringServices) {
            stopDiscovery()
        }

        runningJob?.cancel()
        runningJob =
            scope.launch {
                val serviceInfo =
                    NsdServiceInfo().apply {
                        serviceName = deviceName
                        serviceType = Constants.DISCOVERY_SERVICE_TYPE
                        port = Constants.DISCOVERY_PORT
                    }

                val listener =
                    object : NsdManager.RegistrationListener {
                        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                            Logger.d(TAG) { "Service registered: ${serviceInfo.serviceName}" }
                            _state.value = DiscoveryManagerState.ServiceRegistered
                        }

                        override fun onRegistrationFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int,
                        ) {
                            Logger.e(TAG) { "Service registration failed: errorCode=$errorCode" }
                            _state.value = DiscoveryManagerState.Idle
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

                Logger.d(TAG) { "Registering service..." }
                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            }
    }

    actual fun unregisterService() {
        Logger.d(TAG) { "Unregistering service..." }
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            runningJob?.cancel()
            runningJob = null
        } catch (ex: IllegalArgumentException) {
            Logger.e(TAG, ex) { "Failed to unregister service" }
        } finally {
            registrationListener = null
            _state.value = DiscoveryManagerState.Idle
        }
    }

    actual fun startDiscovery() {
        if (discoveryListener != null) return
        if (_state.value == DiscoveryManagerState.ServiceRegistered) {
            unregisterService()
        }

        runningJob?.cancel()
        runningJob =
            scope.launch {
                val listener =
                    object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(serviceType: String) {
                            Logger.d(TAG) { "Discovery started: $serviceType" }
                        }

                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            if (serviceInfo.hosts.intersect(localAddresses()).isNotEmpty()) return
                            Logger.d(TAG) { "Service found: ${serviceInfo.serviceName}" }
                            resolveService(serviceInfo)
                        }

                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                            val hosts = serviceInfo.hosts
                            if (hosts.intersect(localAddresses()).isNotEmpty()) return
                            Logger.d(TAG) { "Service lost: ${serviceInfo.serviceName} -> $hosts" }
                            _discoveredServers.value
                                .firstOrNull { it.matchesAddresses(hosts) }
                                ?.let { device ->
                                    _discoveredServers.value -= device
                                }
                                ?: run {
                                    _discoveredServers.value
                                        .firstOrNull { it.id.name == serviceInfo.serviceName }
                                        ?.let { device ->
                                            _discoveredServers.value -= device
                                        }
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

                acquireMulticastLock()
                discoveryListener?.let {
                    nsdManager.stopServiceDiscovery(it)
                }
                discoveryListener = listener

                nsdManager.discoverServices(
                    Constants.DISCOVERY_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
                _state.value = DiscoveryManagerState.DiscoveringServices
            }
    }

    actual fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
            runningJob?.cancel()
            runningJob = null
        } catch (ex: IllegalArgumentException) {
            Logger.e(TAG, ex) { "Failed to stop discovery" }
        } finally {
            discoveryListener = null
            _discoveredServers.value = emptySet()
            releaseMulticastLock()
            _state.value = DiscoveryManagerState.Idle
        }
    }

    actual fun setDeviceName(name: String) {
        if (deviceName == name) return
        deviceName = name
        if (_state.value == DiscoveryManagerState.ServiceRegistered) {
            unregisterService()
            registerService()
        }
    }

    actual fun getDiscoveredServers(): Set<Server> = _discoveredServers.value

    actual fun refresh() {
        Logger.i(TAG) { "Refreshing discovery from state ${_state.value}" }
        when (_state.value) {
            DiscoveryManagerState.ServiceRegistered -> {
                unregisterService()
                registerService()
            }

            DiscoveryManagerState.DiscoveringServices -> {
                stopDiscovery()
                startDiscovery()
            }

            DiscoveryManagerState.Idle -> {
                // Nothing to do.
            }
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
                        val name = serviceInfo.serviceName
                        val hosts = serviceInfo.hosts

                        if (hosts.intersect(localAddresses()).isNotEmpty()) return

                        if (hosts.isEmpty()) {
                            Logger.w(TAG) { "Service resolved with no hosts: $name" }
                            return
                        }

                        Logger.d(TAG) { "Service resolved: $name -> $hosts" }
                        _discoveredServers.update { servers ->
                            val new = Server(ServerId(name), hosts)
                            servers
                                .firstOrNull { it.matchesAddresses(hosts) }
                                ?.let { old -> servers + new - old }
                                ?: run { servers + new }
                        }
                    }
                },
            )
        }
    }

    private fun localAddresses(): Set<InetAddress> =
        try {
            java.net.NetworkInterface
                .getNetworkInterfaces()
                ?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterNot { it.isLoopbackAddress }
                ?.toSet()
                ?: emptySet()
        } catch (_: SocketException) {
            emptySet()
        } catch (_: NullPointerException) {
            emptySet()
        }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val lock = wifiManager.createMulticastLock(deviceName)
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

    private val NsdServiceInfo.hosts: Set<InetAddress>
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7
            ) {
                hostAddresses.toSet()
            } else {
                @Suppress("DEPRECATION")
                setOf(host)
            }

    private companion object {
        private val TAG = DiscoveryManager::class
    }
}
