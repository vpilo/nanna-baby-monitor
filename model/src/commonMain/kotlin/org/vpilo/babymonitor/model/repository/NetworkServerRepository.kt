package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device

interface NetworkServerRepository {
    val serverStateFlow: Flow<ServerState>

    suspend fun start(self: Device.LocalServer)

    suspend fun stop()

    suspend fun setCaptureMode(mode: CaptureMode)

    fun setRelayHost(host: String)

    val pairingState: Flow<PairingWindowState>

    fun startPairingWindow()

    fun cancelPairingWindow()

    /** Immediately terminates any live control/audio/video sessions authenticated as [clientId]. */
    suspend fun closeSessionsForClient(clientId: DeviceId)
}
