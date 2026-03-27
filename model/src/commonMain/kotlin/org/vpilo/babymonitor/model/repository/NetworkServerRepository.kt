package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.CaptureMode

interface NetworkServerRepository {
    val serverStateFlow: Flow<ServerState>

    suspend fun start()

    suspend fun stop()

    suspend fun setCaptureMode(mode: CaptureMode)
}
