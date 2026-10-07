package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairingQrPayload.Companion.fromPayloadString
import kotlin.test.Test
import kotlin.test.assertEquals

class PairingQrPayloadTest {
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
        listOf(
            "WIFI:T:nopass;S:SomeOtherQr;;",
            "$PREFIX|$VERSION",
            "$PREFIX|$VERSION|$FINGERPRINT",
            "$PREFIX|$VERSION|$FINGERPRINT|$someDeviceId",
        ).forEach { payload ->
            assertEquals(PairingQrPayload.Invalid.WRONG_QR, payload.fromPayloadString(someDeviceId))
        }
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
        assertEquals(
            PairingQrPayload.Invalid.WRONG_QR,
            "bm|$VERSION|$someDeviceId|AB23CD".fromPayloadString(someDeviceId),
        )
    }

    @Test
    fun rejectsMalformedFingerprints() {
        listOf("", FINGERPRINT.dropLast(1), FINGERPRINT + "0", "g".repeat(64), FINGERPRINT.uppercase()).forEach { fingerprint ->
            assertEquals(
                PairingQrPayload.Invalid.INVALID_SERVER_FINGERPRINT,
                "bm|$VERSION|$fingerprint|$someDeviceId|AB23CD".fromPayloadString(someDeviceId),
            )
        }
    }

    @Test
    fun rejectsMalformedPin() {
        assertEquals(
            PairingQrPayload.Invalid.WRONG_PIN,
            "bm|$VERSION|$FINGERPRINT|$someDeviceId|invalid".fromPayloadString(someDeviceId),
        )
    }

    @Test
    fun rejectsMalformedVersions() {
        listOf("", "hello", "0", "-1", "9999999999999999999999").forEach { version ->
            assertEquals(
                PairingQrPayload.Invalid.WRONG_QR,
                "$PREFIX|$version|$FINGERPRINT|$someDeviceId|AB23CD".fromPayloadString(someDeviceId),
            )
        }
    }

    @Test
    fun rejectsExtraFields() {
        assertEquals(
            PairingQrPayload.Invalid.WRONG_PIN,
            "$PREFIX|$VERSION|$FINGERPRINT|$someDeviceId|AB23CD|extra".fromPayloadString(someDeviceId),
        )
    }

    private companion object {
        private const val PREFIX = PairingQrPayload.QR_PAYLOAD_PREFIX
        private const val VERSION = PairingQrPayload.PAIRING_PROTOCOL_VERSION
        private val FINGERPRINT = "0123456789abcdef".repeat(4)

        private val someDeviceId = DeviceId.random()
    }
}
