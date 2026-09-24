package org.vpilo.babymonitor.network.model.pairing

import kotlinx.serialization.Serializable
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

/**
 * A pairing with a peer.
 * [certFingerprint] is the peer's TLS identity, pinned whenever the peer is the camera; [sharedSecretBase64] is `S`, used by the
 * session handshake in either direction.
 */
@Serializable
data class PairedDevice(
    val deviceId: String,
    val name: String,
    val certFingerprint: String,
    val sharedSecretBase64: String,
) {
    fun asServer(): Device.LocalServer = Device.LocalServer(DeviceId.parse(deviceId), name)

    fun asClient(): Device.Client = Device.Client(DeviceId.parse(deviceId), name)

    override fun toString(): String = "PairedDevice($deviceId)"
}
