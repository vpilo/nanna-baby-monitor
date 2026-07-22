package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow

interface IsConnectionAvailableRepository {
    val isConnectionAvailableFlow: Flow<Boolean>
}
