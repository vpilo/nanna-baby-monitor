package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.DefaultNetworkClientRepository
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.client.NetworkAudioReceiverRepository
import org.vpilo.babymonitor.network.client.NetworkControlDataSource
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.client.NetworkVideoReceiverRepository
import org.vpilo.babymonitor.network.client.RelayDiscoveryDataSource
import org.vpilo.babymonitor.network.client.ServerSelectionDataSource
import org.vpilo.babymonitor.network.common.DiscoveryManager

val networkClientKoinModule: Module =
    module {
        singleOf(::NetworkControlDataSource)
        singleOf(::ServerSelectionDataSource)
        singleOf(::NetworkAudioDataSource)
        singleOf(::NetworkVideoDataSource)
        singleOf(::RelayDiscoveryDataSource)

        singleOf(::NetworkAudioReceiverRepository)
            .bind<StreamingAudioReceiverRepository>()
        singleOf(::NetworkVideoReceiverRepository)
            .bind<StreamingVideoReceiverRepository>()

        singleOf(::DefaultNetworkClientRepository)
            .bind<NetworkClientRepository>()

        singleOf(::DiscoveryManager)
            .withOptions { createdAtStart() }
    }
