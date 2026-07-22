package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.model.pairing.ServerPairingState

interface NetworkServerRepository {
    val serverStateFlow: Flow<ServerState>

    suspend fun start(self: Device.LocalServer)

    suspend fun stop()

    suspend fun setCaptureMode(mode: CaptureMode)

    fun setRelayHost(host: String)

    val pairingState: Flow<ServerPairingState>

    fun startPairingWindow()

    fun cancelPairingWindow()
}
