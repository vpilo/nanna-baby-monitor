package org.vpilo.babymonitor.network.security.protocol

import org.vpilo.babymonitor.network.security.pairing.PairingHello
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class PairingProtocolTest {
    @Test
    fun helloRoundTrips() {
        val hello = PairingHello(Uuid.random(), "Nursery", FINGERPRINT, PUBLIC_KEY)

        assertEquals(hello, parsePairingHelloOrNull(hello.toWireString()))
    }

    @Test
    fun nameContainingSeparatorRoundTrips() {
        val hello = PairingHello(Uuid.random(), "Mum|Dad's phone", FINGERPRINT, PUBLIC_KEY)

        assertEquals(hello, parsePairingHelloOrNull(hello.toWireString()))
    }

    @Test
    fun oldThreeFieldHelloIsRejected() {
        assertNull(parsePairingHelloOrNull("${Uuid.random()}|Nursery|${Base64.encode(PUBLIC_KEY)}"))
    }

    @Test
    fun malformedFingerprintIsRejected() {
        val id = Uuid.random()
        val key = Base64.encode(PUBLIC_KEY)

        assertNull(parsePairingHelloOrNull("$id|Nursery|not-a-fingerprint|$key"))
        assertNull(parsePairingHelloOrNull("$id|Nursery|${FINGERPRINT.uppercase()}|$key"))
        assertNull(parsePairingHelloOrNull("$id|Nursery|${FINGERPRINT.dropLast(2)}|$key"))
    }

    @Test
    fun malformedIdOrKeyIsRejected() {
        val key = Base64.encode(PUBLIC_KEY)

        assertNull(parsePairingHelloOrNull("not-a-uuid|Nursery|$FINGERPRINT|$key"))
        assertNull(parsePairingHelloOrNull("${Uuid.random()}|Nursery|$FINGERPRINT|%%%"))
    }

    private companion object {
        private val FINGERPRINT = "0123456789abcdef".repeat(4)
        private val PUBLIC_KEY = ByteArray(65) { it.toByte() }
    }
}
