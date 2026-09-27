package org.vpilo.babymonitor.network.internal.protocol

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.model.transport.VersionMismatch
import org.vpilo.babymonitor.network.model.transport.VersionMismatchException

/**
 * Version of the communication protocol between devices.
 * Needs to be increased every time wire data between devices is changed!
 */
const val DEVICE_PROTOCOL_VERSION: Int = 1

/**
 * Version of the communication protocol between a device and a relay.
 * Needs to be increased every time wire data to/from a relay is changed!
 */
const val RELAY_PROTOCOL_VERSION: Int = 1

// Custom Ktor close code returned when closing a connection due to version difference.
const val PROTOCOL_VERSION_MISMATCH_CLOSE_CODE: Short = 4000

private const val TAG = "ProtocolVersion"

// Called by the connecting device.
suspend fun WebSocketSession.sendProtocolVersion(version: Int) = send(Frame.Text(version.toString()))

/**
 * Called by the receiving device.
 * Note: the receiving device of a connection only closes the connection on version mismatch. Clients may show errors to the user.
 */
suspend fun WebSocketSession.receiveProtocolVersion(localVersion: Int): Boolean {
    val remoteVersion = (incoming.receive() as? Frame.Text)?.readText()?.toIntOrNull()
    if (remoteVersion == null) {
        Logger.w(TAG) { "Malformed protocol version" }
        close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed protocol version"))
        return false
    }
    if (remoteVersion != localVersion) {
        Logger.w(TAG) { "Protocol version mismatch: local=$localVersion, remote=$remoteVersion" }
        close(CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, localVersion.toString()))
        return false
    }
    return true
}

fun CloseReason.asVersionMismatchExceptionOrNull(localVersion: Int): VersionMismatchException? {
    if (code != PROTOCOL_VERSION_MISMATCH_CLOSE_CODE) return null
    val remoteVersion = message.toIntOrNull() ?: return null
    return VersionMismatchException(localVersion, remoteVersion)
}
