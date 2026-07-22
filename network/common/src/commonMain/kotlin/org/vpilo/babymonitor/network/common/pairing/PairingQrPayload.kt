package org.vpilo.babymonitor.network.common.pairing

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.network.model.pairing.Pin

data class PairingQrPayload(
    val deviceId: DeviceId,
    val pin: Pin,
) {
    fun asPayloadString(): String =
        listOf(QR_PAYLOAD_PREFIX, PAIRING_PROTOCOL_VERSION, deviceId, pin.toString())
            .joinToString(QR_PAYLOAD_SEPARATOR)

    companion object {
        private val TAG = PairingQrPayload::class

        private const val QR_PAYLOAD_PREFIX = "bm"
        private const val QR_PAYLOAD_FIELD_COUNT = 4
        private const val QR_PAYLOAD_SEPARATOR = "|"

        private const val PAIRING_PROTOCOL_VERSION = 1

        fun fromPayloadStringOrNull(payload: String): PairingQrPayload? {
            val parts = payload.split(QR_PAYLOAD_SEPARATOR, limit = QR_PAYLOAD_FIELD_COUNT)
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

            val pin = Pin.fromStringOrNull(parts[3])
            if (pin == null) {
                Logger.w(TAG) { "Invalid PIN '${parts[3]}'" }
                return null
            }

            return PairingQrPayload(
                deviceId = deviceId,
                pin = pin,
            )
        }
    }
}
