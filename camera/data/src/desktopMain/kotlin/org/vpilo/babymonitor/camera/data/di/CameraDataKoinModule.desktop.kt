package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.PlatformAudioCaptureRepository
import org.vpilo.babymonitor.camera.data.PlatformVideoCaptureRepository
import org.vpilo.babymonitor.model.AudioCaptureRepository
import org.vpilo.babymonitor.model.VideoCaptureRepository

actual val cameraDataKoinModule: Module =
    module {
        single<AudioCaptureRepository> { PlatformAudioCaptureRepository() }
        single<VideoCaptureRepository> { PlatformVideoCaptureRepository() }
    }
