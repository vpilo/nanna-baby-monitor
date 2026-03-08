package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.dsl.module

internal expect val cameraRepositoryKoinModule: Module

val cameraDataKoinModule =
    module {
        includes(cameraRepositoryKoinModule)

//        singleOf(::VideoEncoderRepository)
//            .withOptions { qualifier = AppRole.CAMERA }
//            .bind(VideoFeedRepository::class)
    }
