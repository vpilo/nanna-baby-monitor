package org.vpilo.babymonitor.network.internal.discovery.ktx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.Constants
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

internal fun Device.toAttributes(): Map<String, String> =
    mapOf(
        DEVICE_ATTRIBUTE_VERSION to DEVICE_ATTRIBUTE_SCHEMA_VERSION,
        DEVICE_ATTRIBUTE_NAME to name,
        DEVICE_ATTRIBUTE_TYPE to if (this is Device.LocalServer) DEVICE_TYPE_SERVER else DEVICE_TYPE_CLIENT,
    )

/**
 * Checks if this [Device] is available on the local network.
 *
 * Discovery protocols are not reliable to detect when a device drops out or switches between networks.
 * Probe directly its available addresses to find out if it's still reachable.
 *
 * A device might have multiple registered addresses: they're all probed in parallel to be more snappy.
 * Non-local devices are considered reachable.
 */
internal suspend fun Device.isReachable(): Boolean {
    if (this !is Device.LocalServer) {
        Logger.w(TAG) { "Probed non local device: $this" }
        return true
    }
    return withContext(Dispatchers.IO) {
        addresses
            .map { async { it.acceptsConnections() } }
            .awaitAll()
            .any { it }
    }
}

private fun InetAddress.acceptsConnections(): Boolean =
    runCatching {
        Socket().use { it.connect(InetSocketAddress(this, Constants.SERVICE_PORT), PROBE_TIMEOUT_MILLIS) }
        true
    }.getOrDefault(false)

private const val PROBE_TIMEOUT_MILLIS = 2_000

private const val TAG = "Device"
