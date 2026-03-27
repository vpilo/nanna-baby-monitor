package org.vpilo.babymonitor.network.common.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.repository.ServerState

private val byteArrayTrue by lazy { byteArrayOf(1) }
private val byteArrayFalse by lazy { byteArrayOf(0) }

suspend fun WebSocketSession.protocolSendAudio(chunk: EncodedAudioStreamChunk) {
    send(Frame.Binary(fin = true, data = chunk.data))
}

suspend fun WebSocketSession.protocolReceiveAudio(): EncodedAudioStreamChunk = EncodedAudioStreamChunk(incoming.receive().data)

suspend fun WebSocketSession.protocolSendVideo(chunk: EncodedVideoStreamChunk) {
    send(Frame.Binary(fin = true, data = chunk.data))
    send(Frame.Binary(fin = true, data = if (chunk.isKeyFrame) byteArrayTrue else byteArrayFalse))
}

suspend fun WebSocketSession.protocolReceiveVideo(): EncodedVideoStreamChunk {
    val data = incoming.receive().data

    val isKeyFrame =
        incoming.receive().let { frame ->
            val flag = frame.data
            check(flag.size == 1) { "Expected 1 byte for key frame flag" }
            flag[0].toInt() != 0
        }
    return EncodedVideoStreamChunk(data, isKeyFrame)
}

suspend fun WebSocketSession.protocolSendServerState(state: ServerState) {
    val contents = byteArrayOf(
        state.captureMode.ordinal.toByte()
    )
    send(Frame.Binary(fin = true, data = contents))
}

suspend fun WebSocketSession.protocolReceiveServerState(): ServerState {
    val rawData = incoming.receive().data

    val captureModeInt = rawData[0].toInt()
    val captureMode = CaptureMode.entries.getOrNull(captureModeInt)
        ?: throw IllegalArgumentException("Invalid capture mode value: $captureModeInt")
    return ServerState(isAvailable = true, captureMode = captureMode)
}
