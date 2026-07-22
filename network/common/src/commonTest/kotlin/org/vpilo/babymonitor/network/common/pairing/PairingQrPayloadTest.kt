package org.vpilo.babymonitor.network.common.pairing

import org.vpilo.babymonitor.model.repository.DeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingQrPayloadTest {
    @Test
    fun roundTripsThroughEncodeAndDecode() {
        val payload =
            PairingQrPayload(
                deviceId = DeviceId.random(),
                pin = "AB23CD",
            )

        val decoded = PairingQrPayload.fromPayloadStringOrNull(payload.asPayloadString())

        assertEquals(payload, decoded)
    }

    @Test
    fun rejectsWrongPrefix() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("xx|1|${DeviceId.random()}|AB23CD"))
    }

    @Test
    fun rejectsMalformedDeviceId() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|1|not-a-uuid|AB23CD"))
    }

    @Test
    fun rejectsDifferentProtocolVersion() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("xx|5|${DeviceId.random()}|AB23CD"))
    }

    @Test
    fun rejectsWrongFieldCount() {
        assertNull(PairingQrPayload.fromPayloadStringOrNull("bm|1|${DeviceId.random()}|AB23CD|rofl&lol"))
    }
}
