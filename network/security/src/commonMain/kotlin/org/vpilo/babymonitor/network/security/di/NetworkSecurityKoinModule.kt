package org.vpilo.babymonitor.network.security.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.pairing.DefaultPairingStorageRepository

val networkSecurityKoinModule: Module =
    module {
        singleOf(::DefaultPairingStorageRepository)
            .bind<PairingStorageRepository>()
    }
