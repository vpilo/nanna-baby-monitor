package org.vpilo.babymonitor.network.model.pairing

import kotlinx.serialization.Serializable
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

@Serializable
data class PairedClient(
    val deviceId: String,
    val name: String,
    val sharedSecretBase64: String,
    val pairedAtEpochMillis: Long,
) {
    fun asDevice(): Device = Device.Client(DeviceId.parse(deviceId), name)
}
