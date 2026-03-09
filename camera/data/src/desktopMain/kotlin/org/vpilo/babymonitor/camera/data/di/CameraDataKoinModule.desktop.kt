package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.AudioRepository
import org.vpilo.babymonitor.camera.data.CameraRepository
import org.vpilo.babymonitor.camera.data.VideoEncoderRepository
import org.vpilo.babymonitor.model.AudioChunkRepository
import org.vpilo.babymonitor.model.CameraFrameRepository
import org.vpilo.babymonitor.model.VideoFeedRepository

actual val cameraDataKoinModule: Module =
    module {
        factory<CameraFrameRepository> { CameraRepository() }
        factory<AudioChunkRepository> { AudioRepository() }
        factory<VideoFeedRepository> { VideoEncoderRepository() }
    }
