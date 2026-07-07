package org.vpilo.babymonitor.network.common.protocol

/** Bound into each sealed frame's associated data so a malicious relay can't cross-wire streams undetected. */
enum class StreamType(
    val tag: Byte,
) {
    CONTROL(0),
    AUDIO(1),
    VIDEO(2),
}
