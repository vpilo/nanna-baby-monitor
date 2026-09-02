package org.vpilo.babymonitor.network.internal.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull

internal class AndroidDiscoveryListener(
    private val nsdManager: NsdManager,
    private val mutableDiscoveredDevicesFlow: MutableStateFlow<Map<DeviceId, Device>>,
) : NsdManager.DiscoveryListener {
    private var device: Device? = null

    fun reset(device: Device? = null) {
        this.device = device
    }

    override fun onDiscoveryStarted(serviceType: String) {
        Logger.d(DefaultLocalDiscoveryRepository.TAG) { "Discovery started" }
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        // NsdManager wants a new listener for every resolution request.
        @Suppress("DEPRECATION")
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    this@AndroidDiscoveryListener.onServiceResolved(serviceInfo)
                }

                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    this@AndroidDiscoveryListener.onResolveFailed(serviceInfo, errorCode)
                }
            },
        )
    }

    fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        val added = serviceInfo.toDeviceOrNull() ?: return
        if (added.id == device?.id) return

        if (added.addresses.isEmpty()) {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) { "Device resolved with no hosts: $added" }
            return
        }

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

    fun onResolveFailed(
        serviceInfo: NsdServiceInfo,
        errorCode: Int,
    ) {
        Logger.w(DefaultLocalDiscoveryRepository.TAG) {
            "Resolve failed for ${serviceInfo.serviceName} (type: ${serviceInfo.serviceType}): errorCode=$errorCode"
        }
    }
}
