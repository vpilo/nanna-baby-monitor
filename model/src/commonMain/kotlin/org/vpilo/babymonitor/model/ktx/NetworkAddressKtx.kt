package org.vpilo.babymonitor.model.ktx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.model.NetworkAddress
import java.net.InetSocketAddress
import java.net.Socket


suspend fun Set<NetworkAddress>.firstReachableOrNull(port: Int): NetworkAddress? {
    return withContext(Dispatchers.IO) {
        this@firstReachableOrNull
            .map { address -> flow { emit(address.takeIfConnectable(port)) } }
            .merge()
            .firstOrNull { it != null }
    }
}
suspend fun Set<NetworkAddress>.filterReachable(port: Int): Set<NetworkAddress> {
    return withContext(Dispatchers.IO) {
        this@filterReachable
            .map { address -> flow { emit(address.takeIfConnectable(port)) } }
            .merge()
            .filterNotNull()
            .toSet()
    }
}

fun NetworkAddress.takeIfConnectable(port: Int): NetworkAddress? =
    runCatching {
        Socket().use { it.connect(InetSocketAddress(this.address, port), PROBE_TIMEOUT_MILLIS) }
        this
    }.getOrDefault(null)

private const val PROBE_TIMEOUT_MILLIS = 5_000
