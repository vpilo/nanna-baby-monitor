package org.vpilo.babymonitor.network.model.repository

import org.vpilo.babymonitor.model.repository.DeviceId

interface ActiveSessionsRepository {
    suspend fun closeSessions(clientId: DeviceId)
}
