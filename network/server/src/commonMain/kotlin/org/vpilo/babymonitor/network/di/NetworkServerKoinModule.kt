package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.server.DefaultNetworkServerRepository
import org.vpilo.babymonitor.network.server.NetworkAudioSenderRepository
import org.vpilo.babymonitor.network.server.NetworkVideoSenderRepository

val networkServerKoinModule: Module =
    module {
        single<StreamingAudioSenderRepository> { NetworkAudioSenderRepository(get(), get()) }
        single<StreamingVideoSenderRepository> { NetworkVideoSenderRepository(get(), get()) }

        single<NetworkServerRepository> { DefaultNetworkServerRepository(get(), get()) }
    }
