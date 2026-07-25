package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.DefaultIsConnectionAvailableRepository
import org.vpilo.babymonitor.network.client.DefaultNetworkClientRepository
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.client.NetworkAudioReceiverRepository
import org.vpilo.babymonitor.network.client.NetworkControlDataSource
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.client.NetworkVideoReceiverRepository
import org.vpilo.babymonitor.network.client.ServerSelectionDataSource
import org.vpilo.babymonitor.network.client.discovery.DefaultRemoteDiscoveryRepository
import org.vpilo.babymonitor.network.client.pairing.DefaultClientPairingRepository
import org.vpilo.babymonitor.network.internal.di.networkInternalKoinModule
import org.vpilo.babymonitor.network.model.pairing.ClientPairingRepository
import org.vpilo.babymonitor.network.model.repository.IsConnectionAvailableRepository
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository
import org.vpilo.babymonitor.network.security.di.networkSecurityKoinModule

val networkClientKoinModule: Module =
    module {
        includes(networkInternalKoinModule)
        includes(networkSecurityKoinModule)

        singleOf(::ServerSelectionDataSource)
        singleOf(::NetworkControlDataSource)
        singleOf(::NetworkAudioDataSource)
        singleOf(::NetworkVideoDataSource)

        // SharedResourceHolder repositories must be singletons to maintain their state.
        singleOf(::NetworkAudioReceiverRepository)
            .bind<StreamingAudioReceiverRepository>()
        singleOf(::NetworkVideoReceiverRepository)
            .bind<StreamingVideoReceiverRepository>()

        factoryOf(::DefaultIsConnectionAvailableRepository)
            .bind<IsConnectionAvailableRepository>()
        singleOf(::DefaultNetworkClientRepository)
            .bind<NetworkClientRepository>()
        singleOf(::DefaultRemoteDiscoveryRepository)
            .bind<RemoteDiscoveryRepository>()
        singleOf(::DefaultClientPairingRepository)
            .bind<ClientPairingRepository>()
    }
