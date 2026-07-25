package org.vpilo.babymonitor.network.common.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.network.common.crypto.SessionFrameCipher
import org.vpilo.babymonitor.network.model.ServerState

suspend fun WebSocketSession.protocolSendAudio(
    chunk: EncodedAudioStreamChunk,
    cipher: SessionFrameCipher,
) {
    send(Frame.Binary(fin = true, data = cipher.seal(encodeAudioFrame(chunk))))
}

suspend fun WebSocketSession.protocolReceiveAudio(cipher: SessionFrameCipher): EncodedAudioStreamChunk =
    decodeAudioFrame(cipher.open(incoming.receive().data))

suspend fun WebSocketSession.protocolSendVideo(
    chunk: EncodedVideoStreamChunk,
    cipher: SessionFrameCipher,
) {
    send(Frame.Binary(fin = true, data = cipher.seal(encodeVideoFrame(chunk))))
}

suspend fun WebSocketSession.protocolReceiveVideo(cipher: SessionFrameCipher): EncodedVideoStreamChunk =
    decodeVideoFrame(cipher.open(incoming.receive().data))

suspend fun WebSocketSession.sendServerMessage(
    payload: ServerState,
    cipher: SessionFrameCipher,
) = send(Frame.Binary(fin = true, data = cipher.seal(encodeServerMessage(payload))))

suspend fun WebSocketSession.receiveServerMessage(cipher: SessionFrameCipher): ServerMessage =
    decodeServerMessage(cipher.open(incoming.receive().data))
