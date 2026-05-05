package org.vpilo.babymonitor.model.repository

sealed interface ConnectionState {
    data class Disconnected(
        val reason: ErrorReason,
        val additionalInfo: Throwable? = null,
    ) : ConnectionState

    data class Connecting(
        val server: ServerId,
    ) : ConnectionState

    data class Connected(
        val server: ServerId,
    ) : ConnectionState

    enum class ErrorReason {
        ServerNotFound,
        ServerQuit,
        ClientQuit,
        NotConnectedYet,
        ConnectionFailed,
    }
}
