package org.vpilo.babymonitor.network.common

import org.vpilo.babymonitor.model.repository.ServerId
import java.net.InetAddress

class Server(
    val id: ServerId,
    val addresses: Set<InetAddress>,
) : Comparable<Server> {
    constructor(id: ServerId, address: InetAddress) : this(id, setOf(address))

    override fun compareTo(other: Server): Int = id.name.compareTo(other.id.name)

    fun matchesAddresses(matches: Set<InetAddress>): Boolean = addresses.intersect(matches).isNotEmpty()

    override fun toString(): String = "DiscoveredServer('${id.name}' at ${addresses.map { it.hostAddress }})"
}
