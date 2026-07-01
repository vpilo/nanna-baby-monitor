package org.vpilo.babymonitor.network.common.discovery.ktx

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

internal fun Device.toAttributes(): Map<String, String> =
    mapOf(
        DEVICE_ATTRIBUTE_VERSION to DEVICE_ATTRIBUTE_SCHEMA_VERSION,
        DEVICE_ATTRIBUTE_NAME to name,
        DEVICE_ATTRIBUTE_TYPE to if (this is Device.LocalServer) DEVICE_TYPE_SERVER else DEVICE_TYPE_CLIENT,
    )

fun Device.RemoteServer.asTransportString(): String = "$id#$relayHost#$name"

fun Device.RemoteServer.Companion.fromTransportString(transportString: String): Device.RemoteServer? {
    if (transportString.isBlank()) return null
    val splits = transportString.split("#", limit = 3)
    if (splits.size != 3) {
        Logger.w("DeviceTransport") { "Invalid transport string: $transportString" }
        return null
    }
    val (rawId, relayHost, name) = splits
    if (rawId.isBlank() || relayHost.isBlank() || name.isBlank()) {
        Logger.w("DeviceTransport") { "Invalid transport string: $transportString" }
        return null
    }
    val id = DeviceId.parseOrNull(rawId) ?: return null

    return Device.RemoteServer(
        id = id,
        name = name,
        relayHost = relayHost,
    )
}
