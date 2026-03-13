package org.vpilo.babymonitor.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.app.CURRENT_APP_ROLE
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.model.di.AppRole

expect val appPlatformModule: Module

val appSharedKoinModules = listOf(
    module {
        factory<StreamingAudioSenderRepository> {
            when (CURRENT_APP_ROLE) {
                AppRole.CAMERA -> get(qualifier = AppRole.CAMERA)
                AppRole.MONITOR -> get(qualifier = AppRole.MONITOR)
            }
        }
        factory<StreamingVideoSenderRepository> {
            when (CURRENT_APP_ROLE) {
                AppRole.CAMERA -> get(qualifier = AppRole.CAMERA)
                AppRole.MONITOR -> get(qualifier = AppRole.MONITOR)
            }
        }
    },
    appPlatformModule,
)
