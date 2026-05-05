package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface IsConnectionAvailableRepository {
    val isConnectionAvailableFlow: Flow<Boolean>
}
