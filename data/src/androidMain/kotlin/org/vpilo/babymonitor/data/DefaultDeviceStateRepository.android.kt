package org.vpilo.babymonitor.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.DeviceStateRepository

internal actual class DefaultDeviceStateRepository :
    DeviceStateRepository,
    KoinComponent {
    private val context: Context = get()

    override val batteryLevel: Flow<Int> =
        getBatteryLevelFlow(context)
            .onEach { state ->
                Logger.d(TAG) { "Battery level changed: $state" }
            }.onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Battery level retrieval error: ${ex.prettify()}" } }
            }

    override val signalQuality: Flow<Int> =
        getSignalLevelFlow(context)
            .onEach { state ->
                Logger.d(TAG) { "Signal quality changed: $state" }
            }.onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Signal quality retrieval error: ${ex.prettify()}" } }
            }

    override val isInternetAvailable: Flow<Boolean> =
        getIsInternetAvailableFlow(context)
            .onEach { state ->
                Logger.d(TAG) { "Internet availability changed: $state" }
            }.onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Internet availability retrieval error: ${ex.prettify()}" } }
            }

    private companion object {
        private val TAG = DefaultDeviceStateRepository::class
    }
}
