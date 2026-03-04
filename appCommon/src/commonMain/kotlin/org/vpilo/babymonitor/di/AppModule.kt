package org.vpilo.babymonitor.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.CURRENT_APP_ROLE
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.di.AppRole

expect val appPlatformModule: Module

val sharedModule =
    module {
        factory<VideoFeedRepository> {
            when (CURRENT_APP_ROLE) {
                AppRole.CAMERA -> get(qualifier = AppRole.CAMERA)
                AppRole.MONITOR -> get(qualifier = AppRole.MONITOR)
            }
        }
    }
