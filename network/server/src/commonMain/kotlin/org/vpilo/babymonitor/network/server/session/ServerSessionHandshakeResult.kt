package org.vpilo.babymonitor.network.server.session

import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.common.crypto.SessionFrameCipher

/** Result of a successful [serverSessionHandshake]: the per-session cipher and the client it authenticated as. */
data class ServerSessionHandshakeResult(
    val cipher: SessionFrameCipher,
    val clientId: DeviceId,
)
