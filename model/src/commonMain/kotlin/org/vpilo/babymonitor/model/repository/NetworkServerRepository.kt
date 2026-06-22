package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device

interface NetworkServerRepository {
    val serverStateFlow: Flow<ServerState>

    suspend fun start()

    suspend fun stop()

    fun identifySelf(device: Device.LocalServer)

    suspend fun setCaptureMode(mode: CaptureMode)

    fun setRelayHost(host: String)
}
