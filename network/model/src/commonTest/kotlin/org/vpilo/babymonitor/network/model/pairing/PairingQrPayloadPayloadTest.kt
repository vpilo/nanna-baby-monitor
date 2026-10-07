package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairingQrPayload.Companion.fromPayloadString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingQrPayloadPayloadTest {
    @Test
    fun roundTripsThroughEncodeAndDecode() {
        val payload =
            PairingQrPayload.Valid(
                serverFingerprint = FINGERPRINT,
                deviceId = someDeviceId,
                pin = Pin.generate(),
            )

        val encoded = payload.asPayloadString()
        val decoded = encoded.fromPayloadString(expectedDeviceId = someDeviceId)

        assertEquals(payload, decoded)
    }

    @Test
    fun rejectsWrongFieldCount() {
        assertEquals(
            PairingQrPayload.Invalid.WRONG_QR,
            "WIFI:T:nopass;S:SomeOtherQr;;".fromPayloadString(expectedDeviceId = someDeviceId),
        )
        assertEquals(
            PairingQrPayload.Invalid.WRONG_QR,
            "xx|$VERSION|$someDeviceId|AB23CD".fromPayloadString(expectedDeviceId = someDeviceId),
        )
    }

    @Test
    fun rejectsWrongPrefix() {
        assertEquals(
            PairingQrPayload.Invalid.WRONG_QR,
            "xx|$VERSION|$FINGERPRINT|$someDeviceId|AB23CD".fromPayloadString(expectedDeviceId = someDeviceId),
        )
    }

    @Test
    fun rejectsMalformedDeviceId() {
        assertEquals(
            PairingQrPayload.Invalid.INVALID_DEVICE_ID,
            "$PREFIX|$VERSION|$FINGERPRINT|hello-i-am-uuid|AB23CD".fromPayloadString(expectedDeviceId = someDeviceId),
        )
    }


    @Test
    fun rejectsInvalidDeviceId() {
        assertEquals(
            PairingQrPayload.Invalid.INVALID_DEVICE_ID,
            "$PREFIX|$VERSION|$FINGERPRINT|${DeviceId.random()}|AB23CD".fromPayloadString(expectedDeviceId = someDeviceId),
        )
    }

    @Test
    fun rejectsLowerProtocolVersion() {
        assertEquals(
            PairingQrPayload.Invalid.VERSION_MISMATCH_REMOTE_OUTDATED,
            "$PREFIX|${VERSION - 1}|$FINGERPRINT|$someDeviceId|AB23CD".fromPayloadString(expectedDeviceId = someDeviceId),
        )
    }

    @Test
    fun rejectsHigherProtocolVersion() {
        assertEquals(
            PairingQrPayload.Invalid.VERSION_MISMATCH_LOCAL_OUTDATED,
            "$PREFIX|${VERSION + 1}|$FINGERPRINT|$someDeviceId|AB23CD".fromPayloadString(expectedDeviceId = someDeviceId),
        )
    }

    @Test
    fun rejectsLegacyPayloadWithoutFingerprint() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|1|${DeviceId.random()}|AB23CD"))
    }

    @Test
    fun rejectsMalformedFingerprints() {
        listOf("", FINGERPRINT.dropLast(1), FINGERPRINT + "0", "g".repeat(64), FINGERPRINT.uppercase()).forEach { fingerprint ->
            assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|2|$fingerprint|${DeviceId.random()}|AB23CD"))
        }
    }

    @Test
    fun rejectsMalformedPin() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|2|$FINGERPRINT|${DeviceId.random()}|invalid"))
    }

    private companion object {
        private const val PREFIX = PairingQrPayload.QR_PAYLOAD_PREFIX
        private const val VERSION = PairingQrPayload.PAIRING_PROTOCOL_VERSION
        private val FINGERPRINT = "0123456789abcdef".repeat(4)

        private val someDeviceId = DeviceId.random()
    }
}
