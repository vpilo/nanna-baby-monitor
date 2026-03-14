package org.vpilo.babymonitor.network.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.client.NetworkAudioReceiverRepository
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.client.NetworkVideoReceiverRepository

val networkClientKoinModule: Module =
    module {
        singleOf(::NetworkAudioDataSource)
        singleOf(::NetworkVideoDataSource)

        single<StreamingAudioReceiverRepository> { NetworkAudioReceiverRepository(get(), Dispatchers.Default) }
        single<StreamingVideoReceiverRepository> { NetworkVideoReceiverRepository(get(), Dispatchers.Default) }
    }
