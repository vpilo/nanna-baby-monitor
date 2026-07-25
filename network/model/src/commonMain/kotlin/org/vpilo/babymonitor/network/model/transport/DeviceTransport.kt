package org.vpilo.babymonitor.network.model.transport

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

fun Device.RemoteServer.asTransportString(): String = "$id#$relayHost#$name"

fun Device.LocalServer.asTransportString(relayHost: String): String = "$id#$relayHost#$name"

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
