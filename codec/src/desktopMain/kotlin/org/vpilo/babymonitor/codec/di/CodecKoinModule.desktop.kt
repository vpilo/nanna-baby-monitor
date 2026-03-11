package org.vpilo.babymonitor.codec.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.codec.PlatformAudioEncoderRepository
import org.vpilo.babymonitor.codec.PlatformVideoEncoderRepository
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.model.repository.StreamingAudioRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoRepository

actual val codecKoinModule: Module =
    module {
        single<StreamingAudioRepository>(qualifier = AppRole.CAMERA) { PlatformAudioEncoderRepository(get()) }
        single<StreamingVideoRepository>(qualifier = AppRole.CAMERA) { PlatformVideoEncoderRepository(get()) }
    }
