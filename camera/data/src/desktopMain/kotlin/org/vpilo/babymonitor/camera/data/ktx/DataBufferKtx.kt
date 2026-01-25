package org.vpilo.babymonitor.camera.data.ktx

import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.awt.image.DataBufferUShort
import java.nio.ByteBuffer

internal fun DataBuffer.cloneToByteArray(): ByteArray {
    return when (this) {
        is DataBufferByte -> this.data
        is DataBufferUShort -> {
            val shorts = this.data
            val bytes = ByteArray(shorts.size * 2)
            ByteBuffer.wrap(bytes).asShortBuffer().put(shorts)
            bytes
        }
        is DataBufferInt -> {
            val ints = this.data
            val bytes = ByteArray(ints.size * 4)
            ByteBuffer.wrap(bytes).asIntBuffer().put(ints)
            bytes
        }
        else -> throw IllegalArgumentException("Unsupported DataBuffer type: ${this::class.java.name}")
    }
}

