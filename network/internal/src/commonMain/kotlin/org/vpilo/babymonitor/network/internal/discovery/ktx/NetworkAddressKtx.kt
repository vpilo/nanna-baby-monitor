package org.vpilo.babymonitor.network.internal.discovery.ktx

import org.vpilo.babymonitor.model.NetworkAddress

// Ranks addresses by how stable over time they are.
// IPv4 addresses change less often than IPv6 addresses, and especially less often than link-local IPv6 addresses.
internal fun Set<NetworkAddress>.ranked(): Set<NetworkAddress> = sortedBy { address ->
    when {
        // IPv4
        !address.address.contains(':') -> 0

        // IPv6 global/ULA
        !address.address.startsWith("fe80:", ignoreCase = true) -> 1

        // IPv6 link-local
        else -> 3
    }
}
    .toSet()
