package org.vpilo.babymonitor

import org.vpilo.babymonitor.data.di.dataModule
import org.vpilo.babymonitor.presentation.di.presentationModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.vpilo.babymonitor.camera.data.di.cameraDataModule
import org.vpilo.babymonitor.camera.presentation.di.cameraPresentationModule
import org.vpilo.babymonitor.di.appPlatformModule
import org.vpilo.babymonitor.di.sharedModule

fun initializeKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            listOf(
                appPlatformModule,
                sharedModule,
                dataModule,
                presentationModule,
                cameraPresentationModule,
            ) +
                    cameraDataModule,
        )
    }
}
