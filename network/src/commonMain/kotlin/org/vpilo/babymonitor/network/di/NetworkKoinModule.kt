package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.StreamingAudioRepository
import org.vpilo.babymonitor.model.StreamingVideoRepository
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.network.client.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.network.client.StreamingVideoReceiverRepository

val networkKoinModule: Module =
    module {
        singleOf(::StreamingAudioReceiverRepository)
            .withOptions { qualifier = AppRole.MONITOR }
            .bind(StreamingAudioRepository::class)

        singleOf(::StreamingVideoReceiverRepository)
            .withOptions { qualifier = AppRole.MONITOR }
            .bind(StreamingVideoRepository::class)
    }
