package org.vpilo.babymonitor.network.common.crypto

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull

private const val TAG = "PairingQr"

const val PAIRING_PROTOCOL_VERSION = 1

private const val QR_PAYLOAD_PREFIX = "bm"
private const val QR_PAYLOAD_FIELD_COUNT = 4
private const val QR_PAYLOAD_SEPARATOR = "|"

data class PairingQrPayload(
    val protocolVersion: Int,
    val deviceId: DeviceId,
    val pin: String,
) {
    fun asPayloadString(): String = listOf(QR_PAYLOAD_PREFIX, protocolVersion, deviceId, pin).joinToString(QR_PAYLOAD_SEPARATOR)
}

fun String.decodePairingQrPayloadOrNull(): PairingQrPayload? {
    val parts = split(QR_PAYLOAD_SEPARATOR, limit = QR_PAYLOAD_FIELD_COUNT)
    if (parts.size != QR_PAYLOAD_FIELD_COUNT || parts.first() != QR_PAYLOAD_PREFIX) {
        Logger.w(TAG) { "Not a baby monitor QR" }
        return null
    }
    val version = parts[1].toIntOrNull()
    if (version != PAIRING_PROTOCOL_VERSION) {
        Logger.w(TAG) { "Non matching pairing protocol version $version" }
        return null
    }
    val deviceId = parts[2].toDeviceIdOrNull()
    if (deviceId == null) {
        Logger.w(TAG) { "Invalid device ID '$deviceId'" }
        return null
    }

    val pin = parts[3]
    if (pin.length != PAIRING_PIN_LENGTH) {
        Logger.w(TAG) { "Invalid PIN length ${pin.length}" }
        return null
    }

    return PairingQrPayload(
        protocolVersion = version,
        deviceId = deviceId,
        pin = pin,
    )
}
