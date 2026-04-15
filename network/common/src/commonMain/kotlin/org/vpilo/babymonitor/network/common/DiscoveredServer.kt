package org.vpilo.babymonitor.network.common

import org.vpilo.babymonitor.model.repository.ServerId
import java.net.InetAddress

class DiscoveredServer internal constructor(
    val id: ServerId,
    val addresses: Set<InetAddress>,
) : Comparable<DiscoveredServer> {
    override fun compareTo(other: DiscoveredServer): Int = id.name.compareTo(other.id.name)

    fun matchesAddresses(matches: Set<InetAddress>): Boolean = addresses.intersect(matches).isNotEmpty()

    override fun toString(): String = "DiscoveredServer('${id.name}' at ${addresses.map { it.hostAddress }})"
}
