package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.AndroidBackgroundService

actual val platformCameraDataModule = module {
    singleOf(::AndroidBackgroundService)
}
