package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface NetworkServerRepository {
    val stateFlow: Flow<Boolean>

    suspend fun start()

    fun stop()
}
