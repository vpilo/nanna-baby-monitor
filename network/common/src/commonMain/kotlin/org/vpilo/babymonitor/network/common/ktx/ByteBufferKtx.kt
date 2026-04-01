package org.vpilo.babymonitor.network.common.ktx

import java.nio.ByteBuffer

/**
 * Copied from ktor's `io.ktor.util.moveToByteArray`, which is not available for Android.
 */
internal fun ByteBuffer.moveToByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}
