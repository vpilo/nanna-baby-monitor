package org.vpilo.babymonitor.network.internal.discovery.ktx

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.LocalDiscoveryServerDataSource
import org.vpilo.babymonitor.network.model.Constants

internal fun NsdServiceInfo.toDeviceOrNull(addresses: Set<NetworkAddress>): Device? {
    if (!serviceType.contains(Constants.DISCOVERY_SERVICE_TYPE)) return null
    val attrs = attributes ?: return null

    val version = attrs.getString(DEVICE_ATTRIBUTE_VERSION) ?: return null
    if (version != DEVICE_ATTRIBUTE_SCHEMA_VERSION) {
        Logger.w(LocalDiscoveryServerDataSource.TAG) {
            "Device ignored due to version mismatch: I am $DEVICE_ATTRIBUTE_SCHEMA_VERSION, but got $version"
        }
        return null
    }
    val id =
        DeviceId.parseOrNull(serviceName) ?: run {
            Logger.w(LocalDiscoveryServerDataSource.TAG) {
                "Device ignored due to invalid ID: '$serviceName'"
            }
            return null
        }
    val name =
        attrs.getString(DEVICE_ATTRIBUTE_NAME) ?: run {
            Logger.w(LocalDiscoveryServerDataSource.TAG) {
                "Device ignored due to missing name for $id"
            }
            return null
        }

    val primaryAddress = addresses.first()
    return when (val type = attrs.getString(DEVICE_ATTRIBUTE_TYPE)) {
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
            Logger.w(LocalDiscoveryServerDataSource.TAG) { "Device ignored due to unsupported type '$type'" }
            null
        }
    }
}

private fun Map<String, ByteArray>.getString(attributeName: String): String? = this[attributeName]?.toString(Charsets.UTF_8)

internal val NsdServiceInfo.hosts: Set<NetworkAddress>
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7
        ) {
            hostAddresses
        } else {
            @Suppress("DEPRECATION")
            listOf(host)
        }.map { NetworkAddress(it.hostAddress ?: it.hostName) }
            .toSet()
