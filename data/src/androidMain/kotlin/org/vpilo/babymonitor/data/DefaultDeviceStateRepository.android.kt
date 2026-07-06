package org.vpilo.babymonitor.data

import android.content.Context
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import kotlin.time.Duration.Companion.seconds

internal actual class DefaultDeviceStateRepository :
    DeviceStateRepository,
    KoinComponent {
    private val context: Context = get()

    override val batteryLevel: Flow<Int> =
        getBatteryLevelFlow(context)
            .onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Battery level retrieval error: ${ex.prettify()}" } }
            }.distinctUntilChanged()
            .onEach { state ->
                Logger.d(TAG) { "Battery level changed: $state" }
            }

    override val signalQuality: Flow<Int> =
        getSignalLevelFlow(context)
            .onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Signal quality retrieval error: ${ex.prettify()}" } }
            }.distinctUntilChanged()
            .onEach { state ->
                Logger.d(TAG) { "Signal quality changed: $state" }
            }

    @OptIn(FlowPreview::class)
    override val isInternetAvailable: Flow<Boolean> =
        getIsInternetAvailableFlow(context)
            .debounce(INTERNET_STATE_DEBOUNCE_TIMEOUT)
            .drop(1)
            .onCompletion { ex ->
                ex?.let { Logger.e(TAG) { "Internet availability retrieval error: ${ex.prettify()}" } }
            }.onEach { state ->
                Logger.d(TAG) { "Internet availability changed: $state" }
            }

    private companion object {
        private val TAG = DefaultDeviceStateRepository::class

        private val INTERNET_STATE_DEBOUNCE_TIMEOUT = 10.seconds
    }
}
