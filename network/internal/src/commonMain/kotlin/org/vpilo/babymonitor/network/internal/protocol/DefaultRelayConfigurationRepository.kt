package org.vpilo.babymonitor.network.internal.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.model.repository.RelayConfigurationRepository
import org.vpilo.babymonitor.network.model.settings.RelayHost
import org.vpilo.babymonitor.network.model.settings.RelayPassphrase
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class DefaultRelayConfigurationRepository(
    settings: SettingsRepository,
) : RelayConfigurationRepository {
    override val relayConfiguration: Flow<RelayConfiguration> =
        combine(
            settings.flowOf(Setting.RelayHost),
            settings.flowOf(Setting.RelayPassphrase),
        ) { host, passphrase ->
            RelayConfiguration(host = host, passphrase = passphrase)
        }
}
