package org.vpilo.babymonitor.network.model.repository

import org.vpilo.babymonitor.model.Device

interface LocalDiscoveryServerRepository {
    fun register(device: Device.Server)

    fun unregister()
}
