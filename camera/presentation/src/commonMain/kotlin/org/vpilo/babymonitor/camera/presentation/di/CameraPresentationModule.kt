package org.vpilo.babymonitor.camera.presentation.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.presentation.CameraViewModel

val cameraPresentationModule = module {
    viewModelOf(::CameraViewModel)
}
