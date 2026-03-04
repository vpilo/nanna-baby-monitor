package org.vpilo.babymonitor.camera.data.di

import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.CameraFeedRepository
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.di.AppRole

val cameraDataModule = listOf(
    module {
        factory<VideoFeedRepository>(qualifier = AppRole.CAMERA) {
            CameraFeedRepository()
        }
    },
    platformCameraDataModule,
)
