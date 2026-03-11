package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.PlatformAudioCaptureRepository
import org.vpilo.babymonitor.camera.data.PlatformAudioEncoderRepository
import org.vpilo.babymonitor.camera.data.PlatformVideoCaptureRepository
import org.vpilo.babymonitor.camera.data.PlatformVideoEncoderRepository
import org.vpilo.babymonitor.model.AudioCaptureRepository
import org.vpilo.babymonitor.model.StreamingAudioRepository
import org.vpilo.babymonitor.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.StreamingVideoRepository
import org.vpilo.babymonitor.model.di.AppRole

actual val cameraDataKoinModule: Module =
    module {
        single<AudioCaptureRepository> { PlatformAudioCaptureRepository() }
        single<VideoCaptureRepository> { PlatformVideoCaptureRepository() }
        single<StreamingAudioRepository>(qualifier = AppRole.CAMERA) { PlatformAudioEncoderRepository(get()) }
        single<StreamingVideoRepository>(qualifier = AppRole.CAMERA) { PlatformVideoEncoderRepository(get()) }
    }
