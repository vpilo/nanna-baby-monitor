package org.vpilo.babymonitor.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.data.DefaultAppRoleRepository
import org.vpilo.babymonitor.data.DefaultAudioPlaybackRepository
import org.vpilo.babymonitor.data.DefaultDeviceStateRepository
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import org.vpilo.babymonitor.model.repository.DeviceStateRepository

val dataKoinModule: Module =
    module {
        single<AppRoleRepository> { DefaultAppRoleRepository() }
        single<AudioPlaybackRepository> { DefaultAudioPlaybackRepository() }

        single<DeviceStateRepository> { DefaultDeviceStateRepository() }
    }
