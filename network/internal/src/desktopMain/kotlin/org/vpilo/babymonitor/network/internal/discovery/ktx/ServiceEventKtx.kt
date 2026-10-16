package org.vpilo.babymonitor.network.internal.discovery.ktx

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.LocalDiscoveryDataSource
import org.vpilo.babymonitor.network.model.Constants
import javax.jmdns.ServiceEvent

internal fun ServiceEvent.toDeviceOrNull(addresses: Set<NetworkAddress>): Device? {
    if (!type.contains(Constants.DISCOVERY_SERVICE_TYPE)) return null

    val version = info.getPropertyString(DEVICE_ATTRIBUTE_VERSION) ?: return null
    if (version != DEVICE_ATTRIBUTE_SCHEMA_VERSION) {
        Logger.w(LocalDiscoveryDataSource.TAG) {
            "Device ignored due to version mismatch: I am $DEVICE_ATTRIBUTE_SCHEMA_VERSION, but got $version"
        }
        return null
    }
    val id =
        DeviceId.parseOrNull(info.name) ?: run {
            Logger.w(LocalDiscoveryDataSource.TAG) {
                "Device ignored due to invalid ID: '${info.name}'"
            }
            return null
        }
    val name =
        info.getPropertyString(DEVICE_ATTRIBUTE_NAME) ?: run {
            Logger.w(LocalDiscoveryDataSource.TAG) {
                "Device ignored due to missing name for $id"
            }
            return null
        }

    val primaryAddress = addresses.first()
    return when (val type = info.getPropertyString(DEVICE_ATTRIBUTE_TYPE)) {
        DEVICE_TYPE_SERVER -> {
            val additionalAddresses = addresses - primaryAddress
            Device.LocalServer(
                id = id,
                name = name,
                address = primaryAddress,
                additionalAddresses = additionalAddresses,
            )
        }

        DEVICE_TYPE_CLIENT -> {
            Device.Client(
                id = id,
                name = name,
                address = primaryAddress,
            )
        }

        else -> {
            Logger.w(LocalDiscoveryDataSource.TAG) { "Device ignored due to unsupported type '$type'" }
            null
        }
    }
}

internal val ServiceEvent.hosts: Set<NetworkAddress>
    get() = info.inetAddresses.map { NetworkAddress(it.hostAddress ?: it.hostName) }.toSet()
