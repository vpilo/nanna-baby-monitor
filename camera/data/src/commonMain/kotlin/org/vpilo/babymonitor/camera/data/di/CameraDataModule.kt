package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.DefaultCameraRepository
import org.vpilo.babymonitor.camera.model.CameraRepository

val cameraDataModule = listOf(
    module {
        singleOf(::DefaultCameraRepository).bind<CameraRepository>()
    },
    platformCameraDataModule,
)
