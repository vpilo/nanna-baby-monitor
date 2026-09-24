package org.vpilo.babymonitor.network.model.pairing

enum class ClientPairingFailureCause {
    STORAGE_ERROR,
    PROTOCOL_ERROR,
    INVALID_QR,
    WRONG_PIN,
    NO_ACTIVE_PAIRING_WINDOW,
    SERVER_NOT_ON_NETWORK,
    MITM_SUSPECTED,
    CONNECTION_FAILED,
    WRONG_DEVICE,
}
