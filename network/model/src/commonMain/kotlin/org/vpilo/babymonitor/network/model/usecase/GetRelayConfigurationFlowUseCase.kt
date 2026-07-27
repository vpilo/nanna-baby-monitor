package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.model.settings.RelayHost
import org.vpilo.babymonitor.network.model.settings.RelayPassphrase
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class GetRelayConfigurationFlowUseCase(
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<RelayConfiguration> =
        combine(
            settings.flowOf(Setting.RelayHost),
            settings.flowOf(Setting.RelayPassphrase),
        ) { host, passphrase ->
            RelayConfiguration(host = host, passphrase = passphrase)
        }
}
