package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.network.internal.protocol.ServerMessage
import org.vpilo.babymonitor.network.internal.protocol.decodeAudioFrame
import org.vpilo.babymonitor.network.internal.protocol.decodeServerMessage
import org.vpilo.babymonitor.network.internal.protocol.decodeVideoFrame
import org.vpilo.babymonitor.network.internal.protocol.encodeAudioFrame
import org.vpilo.babymonitor.network.internal.protocol.encodeServerMessage
import org.vpilo.babymonitor.network.internal.protocol.encodeVideoFrame
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.security.crypto.SessionFrameCipher

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
