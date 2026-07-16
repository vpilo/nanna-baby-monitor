package org.vpilo.babymonitor.camera.presentation.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.presentation.pairing.CameraQrScannerViewModel

val cameraPresentationKoinModule: Module =
    module {
        viewModel { CameraQrScannerViewModel() }
    }
