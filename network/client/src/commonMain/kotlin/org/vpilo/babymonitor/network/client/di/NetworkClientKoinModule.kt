package org.vpilo.babymonitor.network.client.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.network.client.VideoFeedReceiverRepository

val networkClientKoinModule: Module =
    module {
        singleOf(::VideoFeedReceiverRepository)
            .withOptions { qualifier = AppRole.MONITOR }
            .bind(VideoFeedRepository::class)
    }
