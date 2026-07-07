package org.vpilo.babymonitor.network.common.crypto

import java.security.SecureRandom

const val PAIRING_PIN_LENGTH = 6

// Uppercase letters and digits, excluding visually ambiguous O/0 and I/1.
private const val PAIRING_PIN_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private val secureRandom = SecureRandom()

fun generatePairingPin(): String =
    buildString(PAIRING_PIN_LENGTH) {
        repeat(PAIRING_PIN_LENGTH) {
            append(PAIRING_PIN_CHARSET[secureRandom.nextInt(PAIRING_PIN_CHARSET.length)])
        }
    }
