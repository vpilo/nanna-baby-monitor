package org.vpilo.babymonitor.network.model.pairing

import kotlinx.serialization.Serializable
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

@Serializable
data class PairedServer(
    val deviceId: String,
    val name: String,
    val certFingerprint: String,
    val sharedSecretBase64: String,
) {
    fun asDevice(): Device = Device.LocalServer(DeviceId.parse(deviceId), name)
}
