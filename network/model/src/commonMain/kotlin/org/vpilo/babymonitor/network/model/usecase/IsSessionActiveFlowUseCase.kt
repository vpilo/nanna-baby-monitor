package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.network.model.repository.NetworkServerRepository

/**
 * The current network state of the app as a boolean: active or not.
 * For a server the session is always active, for a client only if there's a connection.
 */
class IsSessionActiveFlowUseCase(
    private val appRoleRepository: AppRoleRepository,
) : KoinComponent {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Boolean> =
        appRoleRepository.appRole
            .flatMapLatest { role ->
                when (role) {
                    AppRole.SERVER -> {
                        get<NetworkServerRepository>().serverStateFlow.map { it.isAvailable }
                    }

                    AppRole.CLIENT -> {
                        get<NetworkClientRepository>().connectionStateFlow.map {
                            it is ConnectionState.Connected || it is ConnectionState.Reconnecting
                        }
                    }

                    AppRole.UNDECIDED -> {
                        flowOf(false)
                    }
                }
            }.distinctUntilChanged()
}
