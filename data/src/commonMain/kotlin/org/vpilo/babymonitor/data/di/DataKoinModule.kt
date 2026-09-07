package org.vpilo.babymonitor.data.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.data.DefaultAppRoleRepository
import org.vpilo.babymonitor.data.DefaultAudioPlaybackRepository
import org.vpilo.babymonitor.data.DefaultDeviceStateRepository
import org.vpilo.babymonitor.data.DefaultLocalClientDeviceRepository
import org.vpilo.babymonitor.data.device.DeviceStateDataSource
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.LocalClientDeviceRepository

val dataKoinModule: Module =
    module {
        singleOf(::DeviceStateDataSource)

        singleOf(::DefaultAppRoleRepository)
            .bind<AppRoleRepository>()

        singleOf(::DefaultAudioPlaybackRepository)
            .bind<AudioPlaybackRepository>()

        factoryOf(::DefaultDeviceStateRepository)
            .bind<DeviceStateRepository>()

        factoryOf(::DefaultLocalClientDeviceRepository)
            .bind<LocalClientDeviceRepository>()
    }
