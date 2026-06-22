package org.vpilo.babymonitor.model

import org.vpilo.babymonitor.model.repository.DeviceId
import java.net.InetAddress

/**
 * Represents a device that can be discovered and connected to.
 *
 * @property id Unique ID of the device.
 * @property name User-defined name of the device.
 * @property addresses IP addresses of the device.
 */
sealed class Device(
    val id: DeviceId,
    val name: String,
    val addresses: Set<InetAddress>,
) : Comparable<Device> {
    init {
        require(name.isNotBlank()) { "Device name cannot be blank" }
    }

    constructor(id: DeviceId, name: String) : this(id, name, emptySet())
    constructor(id: DeviceId, name: String, address: InetAddress) : this(id, name, setOf(address))

    val idString: String
        get() = id.toString()

    override fun compareTo(other: Device): Int =
        name
            .compareTo(other.name)
            .let { if (it == 0) id.compareTo(other.id) else it }

    fun matchesAddresses(matches: Set<InetAddress>): Boolean = addresses.intersect(matches).isNotEmpty()

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + addresses.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Device

        if (id != other.id) return false
        if (name != other.name) return false
        if (addresses != other.addresses) return false

        return true
    }

    override fun toString(): String = "${this::class.simpleName}('$name', id $id, ${addresses.size} addresses)"

    abstract class Server(
        id: DeviceId,
        name: String,
        addresses: Set<InetAddress>,
    ) : Device(id, name, addresses)

    class LocalServer(
        id: DeviceId,
        name: String,
        addresses: Set<InetAddress>,
    ) : Server(id, name, addresses) {
        constructor(id: DeviceId, name: String) : this(id, name, emptySet())
    }

    class RemoteServer(
        id: DeviceId,
        name: String,
        relayHost: String,
    ) : Server(id, name, setOf(InetAddress.getByAddress(relayHost, EMPTY_ADDRESS))) {
        private companion object {
            private val EMPTY_ADDRESS by lazy { ByteArray(4) }
        }
    }

    class Client(
        id: DeviceId,
        name: String,
        addresses: Set<InetAddress>,
    ) : Device(id, name, addresses) {
        constructor(id: DeviceId, name: String) : this(id, name, emptySet())
    }
}
