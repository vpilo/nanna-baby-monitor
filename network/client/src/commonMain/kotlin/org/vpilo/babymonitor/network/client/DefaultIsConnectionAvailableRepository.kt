package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.model.repository.IsConnectionAvailableRepository

internal class DefaultIsConnectionAvailableRepository(
    serverSelectionDataSource: ServerSelectionDataSource,
) : IsConnectionAvailableRepository {
    override val isConnectionAvailableFlow: Flow<Boolean> =
        serverSelectionDataSource.server.map { it != null }
}
