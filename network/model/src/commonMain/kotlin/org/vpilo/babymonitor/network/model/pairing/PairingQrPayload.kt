package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull

sealed interface PairingQrPayload {
    enum class Invalid : PairingQrPayload {
        WRONG_QR,
        VERSION_MISMATCH_REMOTE_OUTDATED,
        VERSION_MISMATCH_LOCAL_OUTDATED,
        INVALID_SERVER_FINGERPRINT,
        INVALID_DEVICE_ID,
        WRONG_PIN,
    }

    data class Valid(
        val serverFingerprint: String,
        val deviceId: DeviceId,
        val pin: Pin,
    ) : PairingQrPayload {
        fun asPayloadString(): String =
            listOf(QR_PAYLOAD_PREFIX, PAIRING_PROTOCOL_VERSION, serverFingerprint, deviceId, pin.toString())
                .joinToString(QR_PAYLOAD_SEPARATOR)
    }

    companion object {
        internal const val QR_PAYLOAD_PREFIX = "bm"
        private const val QR_PAYLOAD_MINIMUM_FIELD_COUNT = 4
        private const val QR_PAYLOAD_FIELD_COUNT = 5
        private const val QR_PAYLOAD_SEPARATOR = "|"

        internal const val PAIRING_PROTOCOL_VERSION = 2

        fun String.fromPayloadString(expectedDeviceId: DeviceId): PairingQrPayload {
            val parts = split(QR_PAYLOAD_SEPARATOR, limit = QR_PAYLOAD_FIELD_COUNT)
            if (parts.size < QR_PAYLOAD_MINIMUM_FIELD_COUNT || parts.first() != QR_PAYLOAD_PREFIX) {
                return Invalid.WRONG_QR
            }

            if (parts.size != QR_PAYLOAD_FIELD_COUNT) return Invalid.WRONG_QR

            val version = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return Invalid.WRONG_QR
            when {
                version < PAIRING_PROTOCOL_VERSION -> return Invalid.VERSION_MISMATCH_REMOTE_OUTDATED
                version > PAIRING_PROTOCOL_VERSION -> return Invalid.VERSION_MISMATCH_LOCAL_OUTDATED
            }

            val fingerprint =
                parts[2]
                    .takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
                    ?: return Invalid.INVALID_SERVER_FINGERPRINT

            val deviceId =
                parts[3]
                    .toDeviceIdOrNull()
                    ?.takeIf { it == expectedDeviceId }
                    ?: return Invalid.INVALID_DEVICE_ID

            val pin =
                Pin.fromStringOrNull(parts[4])
                    ?: return Invalid.WRONG_PIN

            return Valid(
                serverFingerprint = fingerprint,
                deviceId = deviceId,
                pin = pin,
            )
        }
    }
}
