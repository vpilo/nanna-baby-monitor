package org.vpilo.babymonitor.network.internal.discovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.ktx.filterReachable
import org.vpilo.babymonitor.model.ktx.takeIfConnectable
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.repository.DiscoveredDeviceCacheRepository
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class DefaultDiscoveredDeviceCacheRepository(
    private val dataStore: DataStore<Preferences>,
    coroutineContext: CoroutineContext,
) : DiscoveredDeviceCacheRepository {
    private val updateScope = CoroutineScope(coroutineContext)

    init {
        updateScope.launch {
            getStoredCacheFlow().collect { devices ->
                val new =
                    devices
                        .map { device ->
                            this@launch.async(Dispatchers.IO) {
                                if (device is Device.LocalServer) {
                                    val addresses = device.addresses.filterReachable(Constants.SERVICE_PORT)
                                    val address =
                                        device.address.takeIf { it in addresses }
                                            ?: addresses.firstOrNull()
                                            ?: return@async null

                                    Device.LocalServer(device.id, device.name, address, addresses - address)
                                } else {
                                    device.takeIf { it.address.takeIfConnectable(Constants.SERVICE_PORT) != null }
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                        .toSet()

                Logger.d(TAG) { "On cache update, probed ${new.size} out of ${devices.size}: $new" }
                mutableCachedDevicesFlow.value = new
            }
        }
    }

    private val mutableCachedDevicesFlow: MutableStateFlow<Set<Device>> = MutableStateFlow(emptySet())

    override val cachedDevicesFlow: Flow<Set<Device>> =
        @OptIn(ExperimentalCoroutinesApi::class)
        mutableCachedDevicesFlow.asFlow()

    private fun getStoredCacheFlow() =
        dataStore
            .data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                val now = Clock.System.now()
                val expired = mutableListOf<DeviceId>()

                val result =
                    preferences
                        .asMap()
                        .mapNotNull { (key, value) ->
                            if (!key.name.startsWith(KEY_PREFIX)) return@mapNotNull null
                            val savedDevice = (value as? String)?.decode() ?: return@mapNotNull null

                            if (savedDevice.instant < now) {
                                expired.add(savedDevice.device.id)
                                return@mapNotNull null
                            }

                            savedDevice.device
                        }.toSet()

                expired.removeAll()
                result
            }.distinctUntilChanged()

    override suspend fun set(devices: Set<Device>) {
        dataStore.edit { preferences ->
            devices
                .forEach { device ->
                    val key = keyFor(device.id)
                    if (device.address.address.isEmpty() || (device as? Device.LocalServer)?.addresses?.isNotEmpty() != true) {
                        Logger.w(TAG) { "Skipping cache for device ${device.id} with empty address" }
                        preferences.remove(key)
                        return@forEach
                    }

                    val encoded = device.encode()
                    if (preferences[key] == encoded) return@forEach
                    preferences[key] = encoded
                }
        }
    }

    private fun String.decode(): SavedDevice? {
        val lines = this.split("\n")
        if (lines.size < 4) return null
        val instant = Instant.parseOrNull(lines[0]) ?: return null
        val id = DeviceId.parseOrNull(lines[1]) ?: return null
        val name = lines[2]
        val addresses = lines.takeLast(lines.size - 3).map { NetworkAddress(it) }.toSet()
        val primaryAddress = addresses.firstOrNull() ?: return null

        val device =
            when (addresses.size) {
                1 -> Device.LocalServer(id, name, primaryAddress)
                else -> Device.LocalServer(id, name, primaryAddress, addresses - primaryAddress)
            }
        return SavedDevice(instant, device)
    }

    private fun Device.encode(): String {
        val addresses =
            when (this) {
                is Device.LocalServer -> this.addresses
                else -> setOf(this.address)
            }
        val addressesString = addresses.joinToString(separator = "\n") { it.address }

        val now = Clock.System.now() + CACHE_EXPIRATION_TIME
        return "$now\n$id\n$name\n$addressesString"
    }

    private suspend fun List<DeviceId>.removeAll() {
        if (this.isEmpty()) return
        coroutineScope {
            withContext(Dispatchers.IO) {
                dataStore.edit { preferences ->
                    forEach { id -> preferences.remove(keyFor(id)) }
                }
                Logger.d(TAG) { "Removed ${this@removeAll.size} expired cache items" }
            }
        }
    }

    private class SavedDevice(
        val instant: Instant,
        val device: Device,
    )

    private fun keyFor(id: DeviceId): Preferences.Key<String> = stringPreferencesKey(KEY_PREFIX + id)

    private companion object {
        private const val TAG = "DiscoveredDeviceCacheRepository"

        private const val KEY_PREFIX = "device_"

        private val CACHE_EXPIRATION_TIME = 31.days
    }
}
