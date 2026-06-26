package org.vpilo.babymonitor.network.common.discovery.ktx

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.discovery.DefaultLocalDiscoveryRepository
import java.net.InetAddress

internal fun NsdServiceInfo.toDeviceOrNull(): Device? {
    if (!serviceType.contains(Constants.DISCOVERY_SERVICE_TYPE)) return null
    val attrs = attributes ?: return null

    val version = attrs.getString(DEVICE_ATTRIBUTE_VERSION) ?: return null
    if (version != DEVICE_ATTRIBUTE_SCHEMA_VERSION) {
        Logger.w(DefaultLocalDiscoveryRepository.TAG) {
            "Device ignored due to version mismatch: I am $DEVICE_ATTRIBUTE_SCHEMA_VERSION, but got $version"
        }
        return null
    }
    val id =
        DeviceId.parseOrNull(serviceName) ?: run {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) {
                "Device ignored due to invalid ID: '$serviceName'"
            }
            return null
        }
    val name =
        attrs.getString(DEVICE_ATTRIBUTE_NAME) ?: run {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) {
                "Device ignored due to missing name for $id"
            }
            return null
        }

    return when (val type = attrs.getString(DEVICE_ATTRIBUTE_TYPE)) {
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
    }.also { Logger.d("VALERIO") { "Device resolved: $it" } }
}

private fun Map<String, ByteArray>.getString(attributeName: String): String? = this[attributeName]?.toString(Charsets.UTF_8)

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
