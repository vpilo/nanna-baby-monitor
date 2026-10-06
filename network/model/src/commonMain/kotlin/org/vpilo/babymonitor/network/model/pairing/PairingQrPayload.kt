package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull

data class PairingQrPayload(
    val serverFingerprint: String,
    val deviceId: DeviceId,
    val pin: Pin,
) {
    fun asPayloadString(): String =
        listOf(QR_PAYLOAD_PREFIX, PAIRING_PROTOCOL_VERSION, serverFingerprint, deviceId, pin.toString())
            .joinToString(QR_PAYLOAD_SEPARATOR)

    companion object {
        private const val TAG = "PairingQrPayload"

        private const val QR_PAYLOAD_PREFIX = "bm"
        private const val QR_PAYLOAD_FIELD_COUNT = 5
        private const val QR_PAYLOAD_SEPARATOR = "|"

        private const val PAIRING_PROTOCOL_VERSION = 2

        fun fromPayloadStringOrNull(payload: String): PairingQrPayload? {
            val parts = payload.split(QR_PAYLOAD_SEPARATOR, limit = QR_PAYLOAD_FIELD_COUNT)
            if (parts.size != QR_PAYLOAD_FIELD_COUNT || parts.first() != QR_PAYLOAD_PREFIX) {
                Logger.w(TAG) { "Not a Nanna Baby Monitor QR" }
                return null
            }
            val version = parts[1].toIntOrNull()
            if (version != PAIRING_PROTOCOL_VERSION) {
                Logger.w(TAG) { "Non-matching pairing protocol version $version" }
                return null
            }
            val fingerprint = parts[2].takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
            if (fingerprint == null) {
                Logger.w(TAG) { "Invalid server fingerprint '${parts[2]}'" }
                return null
            }
            val deviceId = parts[3].toDeviceIdOrNull()
            if (deviceId == null) {
                Logger.w(TAG) { "Invalid device ID '$deviceId'" }
                return null
            }

            val pin = Pin.fromStringOrNull(parts[4])
            if (pin == null) {
                Logger.w(TAG) { "Invalid PIN '${parts[4]}'" }
                return null
            }

            return PairingQrPayload(
                serverFingerprint = fingerprint,
                deviceId = deviceId,
                pin = pin,
            )
        }
    }
}
