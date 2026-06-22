package org.vpilo.babymonitor.network.common.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.common.discovery.ktx.toDeviceOrNull

internal class AndroidDiscoveryListener(
    private val nsdManager: NsdManager,
) : NsdManager.DiscoveryListener {
    private val _discoveredDevices: MutableStateFlow<Set<Device>> = MutableStateFlow(emptySet())
    val discoveredDevices: StateFlow<Set<Device>> = _discoveredDevices.asStateFlow()

    private var device: Device? = null

    fun setDevice(device: Device) {
        this.device = device
    }

    fun reset() {
        device = null
        _discoveredDevices.value = emptySet()
    }

    override fun onDiscoveryStarted(serviceType: String) {
        Logger.d(DiscoveryManager.TAG) { "Discovery started" }
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        Logger.d(DiscoveryManager.TAG) { "service announce ${serviceInfo.serviceName}" }
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

        if (added.addresses.isEmpty()) {
            Logger.w(DiscoveryManager.TAG) { "Device resolved with no hosts: $added" }
            return
        }

        Logger.i(DiscoveryManager.TAG) { "Device found: $device" }
        _discoveredDevices.update { devices -> devices + added }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        val removed = serviceInfo.toDeviceOrNull() ?: return

        Logger.i(DiscoveryManager.TAG) { "Device lost: $device" }
        _discoveredDevices.update { devices -> devices - removed }
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Logger.d(DiscoveryManager.TAG) { "Discovery stopped" }
    }

    override fun onStartDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
    ) {
        Logger.e(DiscoveryManager.TAG) { "Start discovery failed: errorCode=$errorCode" }
    }

    override fun onStopDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
    ) {
        Logger.e(DiscoveryManager.TAG) { "Stop discovery failed: errorCode=$errorCode" }
    }

    fun onResolveFailed(
        serviceInfo: NsdServiceInfo,
        errorCode: Int,
    ) {
        Logger.w(DiscoveryManager.TAG) {
            "Resolve failed for ${serviceInfo.serviceName} (type: ${serviceInfo.serviceType}): errorCode=$errorCode"
        }
    }
}
