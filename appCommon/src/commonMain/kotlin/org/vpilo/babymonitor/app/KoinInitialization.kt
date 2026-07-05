package org.vpilo.babymonitor.app

import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.vpilo.babymonitor.camera.data.di.cameraDataKoinModule
import org.vpilo.babymonitor.data.di.dataKoinModule
import org.vpilo.babymonitor.di.appSharedKoinModules
import org.vpilo.babymonitor.network.common.di.networkCommonKoinModule
import org.vpilo.babymonitor.network.di.networkClientKoinModule
import org.vpilo.babymonitor.network.di.networkServerKoinModule
import org.vpilo.babymonitor.settings.data.di.settingsDataKoinModule
import org.vpilo.babymonitor.settings.presentation.di.settingsPresentationKoinModule

fun initializeKoin(config: KoinAppDeclaration? = null): Koin {
    val koinApp =
        startKoin {
            config?.invoke(this)
            modules(
                listOf(
                    dataKoinModule,
                    cameraDataKoinModule,
                    networkCommonKoinModule,
                    networkClientKoinModule,
                    networkServerKoinModule,
                    settingsDataKoinModule,
                    settingsPresentationKoinModule,
                ) + appSharedKoinModules,
            )
        }

    return koinApp.koin
}
