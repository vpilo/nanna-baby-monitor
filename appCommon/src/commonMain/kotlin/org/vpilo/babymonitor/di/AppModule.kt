package org.vpilo.babymonitor.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.app.CURRENT_APP_ROLE
import org.vpilo.babymonitor.model.repository.StreamingAudioRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoRepository
import org.vpilo.babymonitor.model.di.AppRole

expect val appPlatformModule: Module

val appSharedKoinModules = listOf(
    module {
        factory<StreamingAudioRepository> {
            when (CURRENT_APP_ROLE) {
                AppRole.CAMERA -> get(qualifier = AppRole.CAMERA)
                AppRole.MONITOR -> get(qualifier = AppRole.MONITOR)
            }
        }
        factory<StreamingVideoRepository> {
            when (CURRENT_APP_ROLE) {
                AppRole.CAMERA -> get(qualifier = AppRole.CAMERA)
                AppRole.MONITOR -> get(qualifier = AppRole.MONITOR)
            }
        }
    },
    appPlatformModule,
)
