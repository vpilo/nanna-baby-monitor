package org.vpilo.babymonitor.app

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.vpilo.babymonitor.camera.data.di.cameraDataKoinModules
import org.vpilo.babymonitor.camera.presentation.di.cameraPresentationKoinModule
import org.vpilo.babymonitor.data.di.dataKoinModule
import org.vpilo.babymonitor.di.appSharedKoinModules
import org.vpilo.babymonitor.network.di.networkKoinModule
import org.vpilo.babymonitor.presentation.di.presentationKoinModule

fun initializeKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            listOf(
                dataKoinModule,
                presentationKoinModule,
                cameraPresentationKoinModule,
                networkKoinModule,
            ) + appSharedKoinModules + cameraDataKoinModules,
        )
    }
}
