package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.CameraVideoCaptureRepository
import org.vpilo.babymonitor.camera.data.MicrophoneAudioCaptureRepository
import org.vpilo.babymonitor.camera.model.AudioCaptureRepository
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository

val cameraDataKoinModule: Module =
    module {
        single<AudioCaptureRepository> { MicrophoneAudioCaptureRepository() }
        single<VideoCaptureRepository> { CameraVideoCaptureRepository() }
    }
