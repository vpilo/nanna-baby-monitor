package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.io.IOException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryServerRepository

internal class DefaultLocalDiscoveryServerRepository(
    private val localDiscoveryServerDataSource: LocalDiscoveryServerDataSource,
) : LocalDiscoveryServerRepository {
    private var device: Device.Server? = null

    override fun register(device: Device.Server) {
        if (this.device == device) {
            return
        }
        if (this.device != null) {
            Logger.i(TAG) { "Service was already registered for ${this.device}! Changing to $device" }
            unregister()
        }
        check(device is Device.LocalServer) { "$device was not a LocalServer" }

        this.device = device

        try {
            Logger.d(TAG) { "Registered: $device" }
            localDiscoveryServerDataSource.registerService(device)
        } catch (ex: IOException) {
            Logger.e(TAG, ex) { "Failed to register" }
        }
    }

    override fun unregister() {
        if (this.device == null) {
            Logger.d(TAG) { "Service was not registered" }
            return
        }

        Logger.d(TAG) { "Unregistered" }
        localDiscoveryServerDataSource.unregisterService()
        this.device = null
    }

    private companion object {
        const val TAG = "DefaultLocalDiscoveryServerRepository"
    }
}
