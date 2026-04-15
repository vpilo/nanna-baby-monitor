package org.vpilo.babymonitor.model.repository

sealed interface NetworkState {
    data class Disconnected(
        val reason: ErrorReason,
        val additionalInfo: Throwable? = null,
    ) : NetworkState

    data class Connecting(
        val server: ServerId,
    ) : NetworkState

    data class Connected(
        val server: ServerId,
    ) : NetworkState

    enum class ErrorReason {
        ServerNotFound,
        ServerQuit,
        ClientQuit,
        NotConnectedYet,
        Unknown,
    }
}
