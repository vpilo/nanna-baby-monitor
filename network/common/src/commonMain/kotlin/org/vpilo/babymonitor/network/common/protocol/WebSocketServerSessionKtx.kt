package org.vpilo.babymonitor.network.common.protocol

import io.ktor.util.moveToByteArray
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.repository.ServerState
import java.nio.ByteBuffer

private val byteArrayTrue by lazy { byteArrayOf(1) }
private val byteArrayFalse by lazy { byteArrayOf(0) }

suspend fun WebSocketSession.protocolSendAudio(chunk: EncodedAudioStreamChunk) {
    send(Frame.Binary(fin = true, data = chunk.data))
}

suspend fun WebSocketSession.protocolReceiveAudio(): EncodedAudioStreamChunk = EncodedAudioStreamChunk(incoming.receive().data)

suspend fun WebSocketSession.protocolSendVideo(chunk: EncodedVideoStreamChunk) {
    val data = ByteBuffer.allocate(chunk.data.size + 1)
        .put(if (chunk.isKeyFrame) byteArrayTrue else byteArrayFalse)
        .put(chunk.data)
        .flip()
        .moveToByteArray()
    send(Frame.Binary(fin = true, data = data))
}

suspend fun WebSocketSession.protocolReceiveVideo(): EncodedVideoStreamChunk {
    val data = ByteBuffer.wrap(incoming.receive().data)
    val isKeyFrame = data.get().toInt() != 0
    return EncodedVideoStreamChunk(data.moveToByteArray(), isKeyFrame)
}

sealed interface ServerMessage {
    val key: Key

    data class State(val payload: ServerState) : ServerMessage {
        override val key = Key.State
    }

    enum class Key {
        State,
    }
}

fun makeServerMessageFrame(payload: ServerState): Frame =
    Frame.Text("${ServerMessage.Key.State}\n${payload.captureMode}")

suspend fun WebSocketSession.receiveServerMessage(): ServerMessage {
    val frame = incoming.receive()
    check(frame is Frame.Text) { "Expected a text frame" }
    val content = frame.readText().split('\n', limit = 2)
    check(content.size == 2) { "Invalid frame format, expected type key and payload" }
    val (key, payload) = content
    return when (key) {
        ServerMessage.Key.State.name ->
            ServerMessage.State(
                ServerState(isAvailable = true, captureMode = CaptureMode.valueOf(payload))
            )

        else -> error("Incoming message key $key was not recognized")
    }
}
