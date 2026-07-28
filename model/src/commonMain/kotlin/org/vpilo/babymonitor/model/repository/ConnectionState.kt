package org.vpilo.babymonitor.model.repository

import androidx.compose.runtime.Stable
import org.vpilo.babymonitor.model.Device

@Stable
sealed interface ConnectionState {
    data class Disconnected(
        val reason: ErrorReason,
        val additionalInfo: Throwable? = null,
    ) : ConnectionState

    data class Connecting(
        val server: Device.Server,
    ) : ConnectionState

    data class Reconnecting(
        val server: Device.Server,
    ) : ConnectionState

    data class Connected(
        val server: Device.Server,
    ) : ConnectionState

    enum class ErrorReason {
        ServerNotFound,
        ServerQuit,
        ClientQuit,
        NotConnectedYet,
        PairingRevoked,
        CertificateMismatch,
    }
}
