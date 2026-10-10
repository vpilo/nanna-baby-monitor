package org.vpilo.babymonitor.settings.data.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.settings.data.DefaultNetworkAddressCacheRepository
import org.vpilo.babymonitor.settings.data.DefaultSettingsRepository
import org.vpilo.babymonitor.settings.data.getCacheStoreFile
import org.vpilo.babymonitor.settings.data.getDataStoreFile
import org.vpilo.babymonitor.settings.model.repository.NetworkAddressCacheRepository
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

val settingsDataKoinModule: Module =
    module {
        single<SettingsRepository> {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    getDataStoreFile()
                }
            DefaultSettingsRepository(dataStore = dataStore, coroutineContext = get())
        }

        single<NetworkAddressCacheRepository> {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    getCacheStoreFile()
                        .also { Logger.d("NET ADDR CACHE") { "Using cache store file: ${it.absolutePath}" } }
                }
            DefaultNetworkAddressCacheRepository(dataStore = dataStore)
        }
    }
