package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.server.DefaultNetworkServerRepository
import org.vpilo.babymonitor.network.server.NetworkAudioSenderRepository
import org.vpilo.babymonitor.network.server.NetworkVideoSenderRepository
import org.vpilo.babymonitor.network.server.RelayServerRegistration
import org.vpilo.babymonitor.network.server.pairing.PairingCoordinator
import org.vpilo.babymonitor.network.server.session.ActiveSessionRegistry

val networkServerKoinModule: Module =
    module {
        singleOf(::RelayServerRegistration)
        singleOf(::PairingCoordinator)
        singleOf(::ActiveSessionRegistry)

        // SharedResourceHolder repositories must be singletons to maintain their state.
        singleOf(::NetworkAudioSenderRepository)
            .bind<StreamingAudioSenderRepository>()
        singleOf(::NetworkVideoSenderRepository)
            .bind<StreamingVideoSenderRepository>()

        singleOf(::DefaultNetworkServerRepository)
            .bind<NetworkServerRepository>()
    }
