package org.vpilo.babymonitor.network.common.discovery.ktx

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.discovery.DefaultLocalDiscoveryRepository
import java.net.InetAddress
import javax.jmdns.ServiceEvent

internal fun ServiceEvent.toDeviceOrNull(): Device? {
    if (!type.contains(Constants.DISCOVERY_SERVICE_TYPE)) return null

    val version = info.getPropertyString(DEVICE_ATTRIBUTE_VERSION) ?: return null
    if (version != DEVICE_ATTRIBUTE_SCHEMA_VERSION) {
        Logger.w(DefaultLocalDiscoveryRepository.TAG) {
            "Device ignored due to version mismatch: I am $DEVICE_ATTRIBUTE_SCHEMA_VERSION, but got $version"
        }
        return null
    }
    val id =
        DeviceId.parseOrNull(info.name) ?: run {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) {
                "Device ignored due to invalid ID: '${info.name}'"
            }
            return null
        }
    val name =
        info.getPropertyString(DEVICE_ATTRIBUTE_NAME) ?: run {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) {
                "Device ignored due to missing name for $id"
            }
            return null
        }

    return when (val type = info.getPropertyString(DEVICE_ATTRIBUTE_TYPE)) {
        DEVICE_TYPE_SERVER -> {
            Device.LocalServer(
                id = id,
                name = name,
                addresses = hosts,
            )
        }

        DEVICE_TYPE_CLIENT -> {
            Device.Client(
                id = id,
                name = name,
                addresses = hosts,
            )
        }

        else -> {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) { "Device ignored due to unsupported type '$type'" }
            null
        }
    }
}

private val ServiceEvent.hosts: Set<InetAddress>
    get() = (info.inet6Addresses?.toSet() ?: emptySet()) + (info.inet4Addresses?.toSet() ?: emptySet())
