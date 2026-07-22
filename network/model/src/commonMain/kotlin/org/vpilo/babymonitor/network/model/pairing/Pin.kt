package org.vpilo.babymonitor.network.model.pairing

import org.jetbrains.annotations.VisibleForTesting
import java.security.SecureRandom

class Pin private constructor(
    private val value: String,
) {
    init {
        require(value.length == PAIRING_PIN_LENGTH) { "Invalid PIN length" }
        require(value.all { it in PAIRING_PIN_CHARSET }) { "Invalid PIN characters" }
    }

    override fun equals(other: Any?): Boolean = other is Pin && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        @VisibleForTesting
        internal const val PAIRING_PIN_LENGTH = 6

        // Uppercase letters and digits, excluding visually ambiguous O/0 and I/1.
        private const val PAIRING_PIN_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        private val secureRandom by lazy { SecureRandom() }

        fun normalize(candidate: String): String = candidate.uppercase().take(PAIRING_PIN_LENGTH)

        fun fromStringOrNull(candidate: String): Pin? =
            candidate
                .takeIf { it.length == PAIRING_PIN_LENGTH && it.all { c -> c in PAIRING_PIN_CHARSET } }
                ?.let { Pin(it) }

        fun generate(): Pin =
            buildString(PAIRING_PIN_LENGTH) {
                repeat(PAIRING_PIN_LENGTH) {
                    append(PAIRING_PIN_CHARSET[secureRandom.nextInt(PAIRING_PIN_CHARSET.length)])
                }
            }.let { Pin(it) }
    }
}
