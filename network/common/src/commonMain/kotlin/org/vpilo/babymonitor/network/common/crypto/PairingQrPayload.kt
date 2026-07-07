package org.vpilo.babymonitor.network.common.crypto

import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull

const val PAIRING_PROTOCOL_VERSION = 1

private const val QR_PAYLOAD_PREFIX = "bm"
private const val QR_PAYLOAD_FIELD_COUNT = 5
private const val QR_PAYLOAD_SEPARATOR = "|"

data class PairingQrPayload(
    val protocolVersion: Int,
    val deviceId: DeviceId,
    val pin: String,
    val hostHint: String,
)

fun PairingQrPayload.encodeToQrText(): String =
    listOf(QR_PAYLOAD_PREFIX, protocolVersion, deviceId, pin, hostHint).joinToString(QR_PAYLOAD_SEPARATOR)

fun String.decodePairingQrPayloadOrNull(): PairingQrPayload? {
    val parts = split(QR_PAYLOAD_SEPARATOR)
    if (parts.size != QR_PAYLOAD_FIELD_COUNT) return null
    if (parts[0] != QR_PAYLOAD_PREFIX) return null
    val version = parts[1].toIntOrNull() ?: return null
    val deviceId = parts[2].toDeviceIdOrNull() ?: return null
    return PairingQrPayload(
        protocolVersion = version,
        deviceId = deviceId,
        pin = parts[3],
        hostHint = parts[4],
    )
}
