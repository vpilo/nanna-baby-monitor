package org.vpilo.babymonitor.network.internal.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.ktx.filterReachable
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.hosts
import org.vpilo.babymonitor.network.internal.discovery.ktx.ranked
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import org.vpilo.babymonitor.network.model.Constants

internal class AndroidDiscoveryListener(
    coroutineScope: CoroutineScope,
    private val nsdManager: NsdManager,
    private val mutableDiscoveredDevicesFlow: MutableStateFlow<Map<DeviceId, Device>>,
    private val onDeviceAddressesUpdated: (DeviceId, Set<NetworkAddress>) -> Unit,
) : NsdManager.DiscoveryListener {

    private val addressValidationScope =
        CoroutineScope(CoroutineName("AddressValidation") + SupervisorJob(coroutineScope.coroutineContext.job))

    private var device: Device? = null

    private val resolutionMutex = Mutex()

    fun reset(device: Device) {
        this.device = device
    }

    fun release() {
        device = null
        addressValidationScope.cancel()
    }

    override fun onDiscoveryStarted(serviceType: String) {
        Logger.d(DefaultLocalDiscoveryRepository.TAG) { "Discovery started" }
    }

    override fun onServiceFound(rawServiceInfo: NsdServiceInfo) {
        // Resolve services serially; only one resolution can be done at a time until Android 14.
        addressValidationScope.launch {
            resolutionMutex.withLock {
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
                            Logger.w(DefaultLocalDiscoveryRepository.TAG) {
                                "Resolve failed for ${serviceInfo.serviceName} (type: ${serviceInfo.serviceType}): errorCode=$errorCode"
                            }
                            deferred.complete(null)
                        }
                    },
                )
                deferred.await()
            }
                ?.let { onServiceResolved(it) }
        }
    }

    suspend fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        val addresses = serviceInfo.hosts.filterReachable(Constants.SERVICE_PORT).ranked()
        val address = addresses.firstOrNull()
        if (address == null) {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) { "Device ignored due to no connectable hosts: $serviceInfo" }
            return
        }
        val added = serviceInfo.toDeviceOrNull(address) ?: return
        if (added.id == device?.id) return

        onDeviceAddressesUpdated(added.id, addresses)

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device found: $added" }
        mutableDiscoveredDevicesFlow.update { it + (added.id to added) }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        val id = DeviceId.parseOrNull(serviceInfo.serviceName) ?: return
        val removed = mutableDiscoveredDevicesFlow.value[id] ?: return

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device lost: $removed" }
        mutableDiscoveredDevicesFlow.update { it - removed.id }
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Logger.d(DefaultLocalDiscoveryRepository.TAG) { "Discovery stopped" }
    }

    override fun onStartDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
    ) {
        Logger.e(DefaultLocalDiscoveryRepository.TAG) { "Start discovery failed: errorCode=$errorCode" }
    }

    override fun onStopDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
    ) {
        Logger.e(DefaultLocalDiscoveryRepository.TAG) { "Stop discovery failed: errorCode=$errorCode" }
    }
}
