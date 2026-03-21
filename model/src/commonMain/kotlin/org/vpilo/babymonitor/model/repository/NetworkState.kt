package org.vpilo.babymonitor.model.repository

import java.net.InetAddress

sealed interface NetworkState {
    data class Disconnected(val reason: ErrorReason, val additionalInfo: Throwable? = null) : NetworkState

    object Connecting : NetworkState {
        override fun toString() = "Connecting()"
    }

    data class Connected(val address: InetAddress, val hasAudio: Boolean, val hasVideo: Boolean) : NetworkState

    enum class ErrorReason {
        ServerNotFound,
        ServerQuit,
        ClientQuit,
        NotConnectedYet,
        Unknown
    }
}
