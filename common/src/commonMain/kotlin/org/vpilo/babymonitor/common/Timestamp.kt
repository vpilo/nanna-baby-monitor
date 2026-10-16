package org.vpilo.babymonitor.common

import kotlin.time.Clock
import kotlin.time.Instant

private val startInstant: Instant = Clock.System.now()

fun timestamp(): String =
    (Clock.System.now() - startInstant).toComponents { hours, minutes, seconds, nanoseconds ->
        buildString {
            append(hours.toString().padStart(2, '0'))
            append(":")
            append(minutes.toString().padStart(2, '0'))
            append(":")
            append(seconds.toString().padStart(2, '0'))
            append(".")
            append((nanoseconds / 1_000_000).toString().padStart(3, '0')) // Milliseconds are enough
        }
    }
