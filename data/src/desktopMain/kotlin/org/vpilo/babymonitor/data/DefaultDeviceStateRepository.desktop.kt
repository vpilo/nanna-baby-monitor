package org.vpilo.babymonitor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.DeviceStateRepository

// Get a new Oshi instance every time, as it will cache data.
internal actual class DefaultDeviceStateRepository : DeviceStateRepository {
    override val batteryLevel: Flow<Int> =
        getBatteryLevelFlow()
            .onEach { state ->
                Logger.d(TAG) { "Battery level changed: $state" }
            }.onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Battery level retrieval error: ${it.prettify()}" } }
            }.distinctUntilChanged()

    override val signalQuality: Flow<Int> =
        getSignalLevelFlow()
            .onEach { state ->
                Logger.d(TAG) { "Signal quality changed: $state" }
            }.onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Signal quality retrieval error: ${ex.prettify()}" } }
            }.distinctUntilChanged()

    override val isInternetAvailable: Flow<Boolean> =
        getIsInternetAvailableFlow()
            .onEach { state ->
                Logger.d(TAG) { "Internet availability changed: $state" }
            }.onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Internet availability retrieval error: ${ex.prettify()}" } }
            }

    internal companion object {
        internal val TAG = DefaultDeviceStateRepository::class
    }
}
