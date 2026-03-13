package org.vpilo.babymonitor.network.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.codec.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.codec.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.model.di.AppRole

val networkKoinModule: Module =
    module {
        singleOf(::StreamingAudioReceiverRepository)
            .withOptions { qualifier = AppRole.MONITOR }
            .bind(StreamingAudioSenderRepository::class)

        singleOf(::StreamingVideoReceiverRepository)
            .withOptions { qualifier = AppRole.MONITOR }
            .bind(StreamingVideoSenderRepository::class)
    }
