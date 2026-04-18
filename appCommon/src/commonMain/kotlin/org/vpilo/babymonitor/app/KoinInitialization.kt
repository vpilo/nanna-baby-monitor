package org.vpilo.babymonitor.app

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.vpilo.babymonitor.camera.data.di.cameraDataKoinModule
import org.vpilo.babymonitor.camera.presentation.di.cameraPresentationKoinModule
import org.vpilo.babymonitor.data.di.dataKoinModule
import org.vpilo.babymonitor.di.appSharedKoinModules
import org.vpilo.babymonitor.network.di.networkClientKoinModule
import org.vpilo.babymonitor.network.di.networkServerKoinModule
import org.vpilo.babymonitor.settings.data.di.settingsDataKoinModule
import org.vpilo.babymonitor.settings.presentation.di.settingsPresentationKoinModule

fun initializeKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            listOf(
                dataKoinModule,
                cameraDataKoinModule,
                cameraPresentationKoinModule,
                networkClientKoinModule,
                networkServerKoinModule,
                settingsDataKoinModule,
                settingsPresentationKoinModule,
            ) + appSharedKoinModules,
        )
    }
}
