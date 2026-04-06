package org.vpilo.babymonitor.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL
import org.vpilo.babymonitor.model.repository.DeviceStateRepository

internal actual class DefaultDeviceStateRepository :
    DeviceStateRepository,
    KoinComponent {
    private val context: Context = get()

    override val batteryLevel: Flow<Int> =
        flow {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            while (true) {
                val batteryStatus: Intent? = context.registerReceiver(null, filter)
                if (batteryStatus == null) {
                    emit(DEVICE_STATE_DATA_UNAVAILABLE)
                    return@flow
                }
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, DEVICE_STATE_DATA_UNAVAILABLE)

                emit(level.coerceIn(0, 100))
                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }.onCompletion { ex ->
            if (ex != null) {
                Logger.e(TAG) { "Battery level retrieval error: ${ex.message}" }
            }
        }

    override val signalQuality: Flow<Int> =
        flow {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            while (true) {
                emit(
                    if (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                        DEVICE_STATE_DATA_UNAVAILABLE
                    } else {
                        val quality = getWifiSignalQuality(wifiManager)
                        quality
                    },
                )
                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }.onCompletion { ex ->
            if (ex != null) {
                Logger.e(TAG) { "Signal quality retrieval error: ${ex.message}" }
            }
        }

    @Suppress("DEPRECATION")
    private fun getWifiSignalQuality(wifiManager: WifiManager): Int {
        val rssi = wifiManager.connectionInfo.rssi
        if (rssi == UNKNOWN_RSSI) return DEVICE_STATE_DATA_UNAVAILABLE
        return WifiManager.calculateSignalLevel(rssi, 101).coerceIn(0, 100)
    }

    private companion object {
        private const val UNKNOWN_RSSI = -127

        private val TAG = DefaultDeviceStateRepository::class
    }
}
