package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.network.model.RelayConfiguration

interface RelayConfigurationRepository {
    val relayConfiguration: Flow<RelayConfiguration>
}
