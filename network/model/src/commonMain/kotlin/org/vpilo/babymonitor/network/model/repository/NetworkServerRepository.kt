package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.model.pairing.ServerPairingState
import org.vpilo.babymonitor.network.model.transport.VersionMismatch

interface NetworkServerRepository {
    val serverStateFlow: Flow<ServerState>

    val pairingState: Flow<ServerPairingState>

    val relayVersionMismatchFlow: Flow<VersionMismatch?>

    suspend fun start(self: Device.LocalServer)

    suspend fun stop()

    suspend fun setCaptureMode(mode: CaptureMode)

    fun startPairingWindow()

    fun cancelPairingWindow()
}
