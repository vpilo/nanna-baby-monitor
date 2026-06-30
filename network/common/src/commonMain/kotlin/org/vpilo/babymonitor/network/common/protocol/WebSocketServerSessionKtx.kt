package org.vpilo.babymonitor.network.common.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.ServerState

suspend fun WebSocketSession.protocolSendAudio(chunk: EncodedAudioStreamChunk) {
    send(Frame.Binary(fin = true, data = chunk.data))
}

suspend fun WebSocketSession.protocolReceiveAudio(): EncodedAudioStreamChunk = EncodedAudioStreamChunk(incoming.receive().data)

private const val VIDEO_HEADER_SIZE = 1 + 1 + 4 + 4

suspend fun WebSocketSession.protocolSendVideo(chunk: EncodedVideoStreamChunk) {
    val frame = ByteArray(VIDEO_HEADER_SIZE + chunk.data.size)
    frame[0] = if (chunk.isKeyFrame) 1 else 0
    frame[1] = (chunk.rotation / 90).toByte()
    frame.storeIntAt(2, chunk.frameWidth)
    frame.storeIntAt(6, chunk.frameHeight)
    chunk.data.copyInto(frame, destinationOffset = VIDEO_HEADER_SIZE)
    send(Frame.Binary(fin = true, data = frame))
}

suspend fun WebSocketSession.protocolReceiveVideo(): EncodedVideoStreamChunk {
    val data = incoming.receive().data
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

fun makeServerMessageFrame(payload: ServerState): Frame =
    Frame.Text("${ServerMessage.Key.State}\n${payload.captureMode},${payload.batteryLevel},${payload.signalQuality}")

suspend fun WebSocketSession.receiveServerMessage(): ServerMessage {
    val frame = incoming.receive()
    check(frame is Frame.Text) { "Expected a text frame" }
    val content = frame.readText().split('\n', limit = 2)
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
