package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.CameraFeedRepository
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.di.AppRole

val cameraDataKoinModule =
    module {
        singleOf(::CameraFeedRepository)
            .withOptions { qualifier = AppRole.CAMERA }
            .bind(VideoFeedRepository::class)
    }
