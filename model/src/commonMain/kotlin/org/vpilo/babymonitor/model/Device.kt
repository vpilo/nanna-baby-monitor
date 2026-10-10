package org.vpilo.babymonitor.model

import androidx.compose.runtime.Stable
import org.vpilo.babymonitor.model.repository.DeviceId

/**
 * Represents a device that can be discovered and connected to.
 *
 * @property id Unique ID of the device.
 * @property name User-defined name of the device.
 * @property address Network address of the device.
 */
@Stable
sealed class Device(
    val id: DeviceId,
    val name: String,
    val address: NetworkAddress,
) : Comparable<Device> {
    init {
        require(name.isNotBlank()) { "Device name cannot be blank" }
    }

    val idString: String
        get() = id.toString()

    override fun compareTo(other: Device): Int =
        name
            .compareTo(other.name)
            .let { if (it == 0) id.compareTo(other.id) else it }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + address.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Device

        if (id != other.id) return false
        if (name != other.name) return false
        if (address != other.address) return false

        return true
    }

    override fun toString(): String =
        "${this::class.simpleName}($id $address)"

    @Stable
    abstract class Server(
        id: DeviceId,
        name: String,
        address: NetworkAddress,
    ) : Device(id, name, address) {
        companion object
    }

    @Stable
    class LocalServer(
        id: DeviceId,
        name: String,
        address: NetworkAddress,
    ) : Server(id, name, address) {
        constructor(id: DeviceId, name: String) : this(id, name, NO_ADDRESS)
    }

    @Stable
    class RemoteServer(
        id: DeviceId,
        name: String,
        address: NetworkAddress,
    ) : Server(id, name, address) {
        companion object
    }

    @Stable
    class Client(
        id: DeviceId,
        name: String,
        address: NetworkAddress,
    ) : Device(id, name, address) {
        constructor(id: DeviceId, name: String) : this(id, name, NO_ADDRESS)
    }

    @Stable
    class Relay(
        id: DeviceId,
        name: String,
        address: NetworkAddress,
    ) : Device(id, name, address)

    private companion object {
        private val NO_ADDRESS = NetworkAddress("")
    }
}
