package org.vpilo.babymonitor.network.internal.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.ktx.filterReachable
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.hosts
import org.vpilo.babymonitor.network.internal.discovery.ktx.ranked
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import org.vpilo.babymonitor.network.model.Constants
import kotlin.coroutines.CoroutineContext

internal class AndroidDiscoveryListener(
    coroutineContext: CoroutineContext,
    private val nsdManager: NsdManager,
    private val localId: DeviceId,
    private val discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
) : NsdManager.DiscoveryListener {
    private val addressValidationScope =
        CoroutineScope(coroutineContext + SupervisorJob())

    private val resolutionMutex = Mutex()

    fun release() {
        addressValidationScope.cancel()
    }

    override fun onDiscoveryStarted(serviceType: String) {
        Logger.d(LocalDiscoveryDataSource.TAG) { "Discovery started" }
    }

    override fun onServiceFound(rawServiceInfo: NsdServiceInfo) {
        // Resolve services serially; only one resolution can be done at a time until Android 14.
        addressValidationScope.launch {
            resolutionMutex
                .withLock {
                    val deferred = CompletableDeferred<NsdServiceInfo?>()
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(
                        rawServiceInfo,
                        // NsdManager wants a new listener for every resolution request.
                        object : NsdManager.ResolveListener {
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                deferred.complete(serviceInfo)
                            }

                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo,
                                errorCode: Int,
                            ) {
                                Logger.w(LocalDiscoveryDataSource.TAG) {
                                    "Resolve failed for ${serviceInfo.serviceName} (type: ${serviceInfo.serviceType}): errorCode=$errorCode"
                                }
                                deferred.complete(null)
                            }
                        },
                    )
                    deferred.await()
                }?.let { onServiceResolved(it) }
        }
    }

    suspend fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        val addresses = serviceInfo.hosts.filterReachable(Constants.SERVICE_PORT).ranked()
        if (addresses.isEmpty()) {
            Logger.w(LocalDiscoveryDataSource.TAG) { "Device ignored due to no connectable hosts: $serviceInfo" }
            return
        }
        val added = serviceInfo.toDeviceOrNull(addresses) ?: return
        if (added.id == localId) return

        discoveredDevices.value[added.id]
            ?.let { return }
            ?: run {
                Logger.i(LocalDiscoveryDataSource.TAG) { "Device found: $added" }
            }

        discoveredDevices.update { it + (added.id to added) }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        val id = DeviceId.parseOrNull(serviceInfo.serviceName) ?: return
        val removed = discoveredDevices.value[id] ?: return

        Logger.i(LocalDiscoveryDataSource.TAG) { "Device lost: $removed" }
        discoveredDevices.update { it - removed.id }
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Logger.d(LocalDiscoveryDataSource.TAG) { "Discovery stopped" }
    }

    override fun onStartDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
    ) {
        Logger.e(LocalDiscoveryDataSource.TAG) { "Start discovery failed: errorCode=$errorCode" }
    }

    override fun onStopDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
    ) {
        Logger.e(LocalDiscoveryDataSource.TAG) { "Stop discovery failed: errorCode=$errorCode" }
    }
}
