package org.vpilo.babymonitor.network.common.protocol

import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.network.model.ServerState

private const val VIDEO_HEADER_SIZE = 1 + 1 + 4 + 4

fun encodeAudioFrame(chunk: EncodedAudioStreamChunk): ByteArray = chunk.data

fun decodeAudioFrame(data: ByteArray): EncodedAudioStreamChunk = EncodedAudioStreamChunk(data)

fun encodeVideoFrame(chunk: EncodedVideoStreamChunk): ByteArray {
    val frame = ByteArray(VIDEO_HEADER_SIZE + chunk.data.size)
    frame[0] = if (chunk.isKeyFrame) 1 else 0
    frame[1] = (chunk.rotation / 90).toByte()
    frame.storeIntAt(2, chunk.frameWidth)
    frame.storeIntAt(6, chunk.frameHeight)
    chunk.data.copyInto(frame, destinationOffset = VIDEO_HEADER_SIZE)
    return frame
}

fun decodeVideoFrame(data: ByteArray): EncodedVideoStreamChunk {
    val isKeyFrame = data[0].toInt() != 0
    val rotation = (data[1].toInt() and 0xFF) * 90
    val frameWidth = data.getIntAt(2)
    val frameHeight = data.getIntAt(6)
    return EncodedVideoStreamChunk(
        data = data.copyOfRange(VIDEO_HEADER_SIZE, data.size),
        isKeyFrame = isKeyFrame,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        rotation = rotation,
    )
}

sealed interface ServerMessage {
    val key: Key

    data class State(
        val payload: ServerState,
    ) : ServerMessage {
        override val key = Key.State
    }

    enum class Key {
        State,
    }
}

fun encodeServerMessage(payload: ServerState): ByteArray =
    "${ServerMessage.Key.State}\n${payload.captureMode},${payload.batteryLevel},${payload.signalQuality}".encodeToByteArray()

fun decodeServerMessage(data: ByteArray): ServerMessage {
    val content = data.decodeToString().split('\n', limit = 2)
    check(content.size == 2) { "Invalid frame format, expected type key and payload" }
    val (key, payload) = content
    return when (key) {
        ServerMessage.Key.State.name -> {
            val (captureMode, batteryLevel, signalQuality) = payload.split(',')
            ServerMessage.State(
                ServerState(
                    isAvailableOnLocalNetwork = true, // Ignored by clients
                    isAvailableOnRelay = true, // Ignored by clients
                    captureMode = CaptureMode.valueOf(captureMode),
                    batteryLevel = batteryLevel.toIntOrNull() ?: DEVICE_STATE_DATA_UNAVAILABLE,
                    signalQuality = signalQuality.toIntOrNull() ?: DEVICE_STATE_DATA_UNAVAILABLE,
                ),
            )
        }

        else -> {
            error("Incoming message key $key was not recognized")
        }
    }
}

private fun ByteArray.storeIntAt(
    index: Int,
    value: Int,
) {
    this[index + 0] = (value shr 24).toByte()
    this[index + 1] = (value shr 16).toByte()
    this[index + 2] = (value shr 8).toByte()
    this[index + 3] = value.toByte()
}

private fun ByteArray.getIntAt(index: Int): Int =
    ((this[index + 0].toInt() and 0xFF) shl 24) or
        ((this[index + 1].toInt() and 0xFF) shl 16) or
        ((this[index + 2].toInt() and 0xFF) shl 8) or
        (this[index + 3].toInt() and 0xFF)
