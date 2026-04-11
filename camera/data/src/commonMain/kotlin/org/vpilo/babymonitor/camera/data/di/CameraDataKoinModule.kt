package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.CameraVideoCaptureRepository
import org.vpilo.babymonitor.camera.data.MicrophoneAudioCaptureRepository
import org.vpilo.babymonitor.camera.model.AudioCaptureRepository
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository

val cameraDataKoinModule: Module =
    module {
        singleOf(::MicrophoneAudioCaptureRepository)
            .bind<AudioCaptureRepository>()
        singleOf(::CameraVideoCaptureRepository)
            .bind<VideoCaptureRepository>()
    }
