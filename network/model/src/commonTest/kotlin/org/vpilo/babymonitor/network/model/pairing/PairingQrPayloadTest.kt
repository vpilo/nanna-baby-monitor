package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.model.repository.DeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingQrPayloadTest {
    @Test
    fun roundTripsThroughEncodeAndDecode() {
        val payload =
            PairingQrPayload(
                serverFingerprint = FINGERPRINT,
                deviceId = DeviceId.random(),
                pin = Pin.generate(),
            )

        val decoded = PairingQrPayload.fromPayloadStringOrNull(payload.asPayloadString())

        assertEquals(payload, decoded)
    }

    @Test
    fun rejectsWrongPrefix() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("xx|2|$FINGERPRINT|${DeviceId.random()}|AB23CD"))
    }

    @Test
    fun rejectsMalformedDeviceId() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|2|$FINGERPRINT|not-a-uuid|AB23CD"))
    }

    @Test
    fun rejectsDifferentProtocolVersion() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|5|$FINGERPRINT|${DeviceId.random()}|AB23CD"))
    }

    @Test
    fun rejectsWrongFieldCount() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|2|$FINGERPRINT|${DeviceId.random()}|AB23CD|rofl&lol"))
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
        private val FINGERPRINT = "0123456789abcdef".repeat(4)
    }
}
