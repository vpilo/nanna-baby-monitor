package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.CameraRepository
import org.vpilo.babymonitor.model.CameraFrameRepository

internal actual val cameraRepositoryKoinModule: Module =
    module {
        factory<CameraFrameRepository> { CameraRepository() }
    }
