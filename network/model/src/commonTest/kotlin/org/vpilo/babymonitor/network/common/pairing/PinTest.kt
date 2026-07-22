package org.vpilo.babymonitor.network.common.pairing

import org.vpilo.babymonitor.network.model.pairing.Pin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinTest {
    private val ambiguousChars = setOf('O', '0', 'I', '1')

    @Test
    fun generatesPinsOfTheExpectedLengthAndCharset() {
        repeat(200) {
            val pin = Pin.generate().toString()
            assertEquals(Pin.PAIRING_PIN_LENGTH, pin.length)
            assertTrue(pin.all { it.isUpperCase() || it.isDigit() })
            assertTrue(pin.none { it in ambiguousChars })
        }
    }
}
