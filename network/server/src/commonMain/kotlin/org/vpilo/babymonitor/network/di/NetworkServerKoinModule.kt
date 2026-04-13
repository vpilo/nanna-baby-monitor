package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.server.DefaultNetworkServerRepository
import org.vpilo.babymonitor.network.server.NetworkAudioSenderRepository
import org.vpilo.babymonitor.network.server.NetworkVideoSenderRepository

val networkServerKoinModule: Module =
    module {
        singleOf(::NetworkAudioSenderRepository)
            .bind<StreamingAudioSenderRepository>()
        singleOf(::NetworkVideoSenderRepository)
            .bind<StreamingVideoSenderRepository>()

        singleOf(::DefaultNetworkServerRepository)
            .bind<NetworkServerRepository>()

        singleOf(::DiscoveryManager)
            .withOptions { createdAtStart() }
    }
