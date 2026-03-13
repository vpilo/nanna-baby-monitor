package org.vpilo.babymonitor.codec.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.codec.AudioDecoderRepository
import org.vpilo.babymonitor.codec.AudioEncoderRepository
import org.vpilo.babymonitor.codec.VideoDecoderRepository
import org.vpilo.babymonitor.codec.VideoEncoderRepository
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository

actual val codecKoinModule: Module =
    module {
        // Encoders (camera role)
        single<StreamingAudioSenderRepository>(qualifier = AppRole.CAMERA) { AudioEncoderRepository(get()) }
        single<StreamingVideoSenderRepository>(qualifier = AppRole.CAMERA) { VideoEncoderRepository(get()) }

        // Decoders (monitor role)
        single<StreamingVideoReceiverRepository> { VideoDecoderRepository(get(qualifier = AppRole.MONITOR)) }
        single<StreamingAudioReceiverRepository> { AudioDecoderRepository(get(qualifier = AppRole.MONITOR)) }
    }
