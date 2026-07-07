package org.vpilo.babymonitor.network.common.crypto

import org.vpilo.babymonitor.model.repository.DeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingQrPayloadTest {
    @Test
    fun roundTripsThroughEncodeAndDecode() {
        val payload =
            PairingQrPayload(
                protocolVersion = PAIRING_PROTOCOL_VERSION,
                deviceId = DeviceId.random(),
                pin = "AB23CD",
                hostHint = "192.168.1.42",
            )

        val decoded = payload.encodeToQrText().decodePairingQrPayloadOrNull()

        assertEquals(payload, decoded)
    }

    @Test
    fun rejectsWrongPrefix() {
        assertNull("xx|1|${DeviceId.random()}|AB23CD|host".decodePairingQrPayloadOrNull())
    }

    @Test
    fun rejectsMalformedDeviceId() {
        assertNull("bm|1|not-a-uuid|AB23CD|host".decodePairingQrPayloadOrNull())
    }

    @Test
    fun rejectsWrongFieldCount() {
        assertNull("bm|1|${DeviceId.random()}|AB23CD".decodePairingQrPayloadOrNull())
    }
}
