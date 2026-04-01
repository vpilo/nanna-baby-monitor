package org.vpilo.babymonitor.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import kotlin.time.Duration.Companion.minutes

internal actual class DefaultDeviceStateRepository : DeviceStateRepository, KoinComponent {
    private val context: Context = get()

    override val batteryLevel: Flow<Int> = flow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level < 0) {
                emit(DEVICE_STATE_DATA_UNAVAILABLE)
                return@flow
            }
            emit(level.coerceIn(0, 100))
            delay(1.minutes)
        }
    }

    override val signalQuality: Flow<Int> = flow {
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
            delay(1.minutes)
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
    }
}
