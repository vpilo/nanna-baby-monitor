package org.vpilo.babymonitor.network.client.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.network.client.VideoFeedReceiverRepository

val networkClientKoinModule: Module =
    module {
        factory<VideoFeedRepository>(qualifier = AppRole.MONITOR) {
            VideoFeedReceiverRepository()
        }
    }
