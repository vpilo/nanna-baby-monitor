package org.vpilo.babymonitor.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.settings.data.ktx.getSafeFlow
import org.vpilo.babymonitor.settings.model.repository.NetworkAddressCacheRepository

class DefaultNetworkAddressCacheRepository(
    private val dataStore: DataStore<Preferences>,
) : NetworkAddressCacheRepository {

    override suspend fun get(id: DeviceId): Set<NetworkAddress> {
        val value = dataStore.getSafeFlow().first()[keyFor(id)]?.takeIf { it.isNotEmpty() } ?: (return emptySet<NetworkAddress>()
            .also { Logger.d("NET ADDR CACHE") { "Retrieved NO cache for device $id" } })
        return value.split(",").map { NetworkAddress(it) }.toSet()
            .also { Logger.d("NET ADDR CACHE") { "Retrieved cache for device $id: $it" } }
    }

    override suspend fun put(id: DeviceId, addresses: Set<NetworkAddress>) {
        dataStore.edit { settings ->
            settings[keyFor(id)] = addresses.joinToString(separator = ",") { it.address }.also {
                Logger.d("NET ADDR CACHE") { "Updated cache for device $id: $it" }
            }
        }
    }

    private fun keyFor(id: DeviceId): Preferences.Key<String> = stringPreferencesKey("device_${id}_addresses")
}
