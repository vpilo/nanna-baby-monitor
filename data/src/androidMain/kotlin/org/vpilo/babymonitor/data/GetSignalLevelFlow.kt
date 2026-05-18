package org.vpilo.babymonitor.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL

private const val UNKNOWN_RSSI = -127

internal fun getSignalLevelFlow(context: Context): Flow<Int> =
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
    }

@Suppress("DEPRECATION")
private fun getWifiSignalQuality(wifiManager: WifiManager): Int {
    val rssi = wifiManager.connectionInfo.rssi
    if (rssi == UNKNOWN_RSSI) return DEVICE_STATE_DATA_UNAVAILABLE
    return WifiManager.calculateSignalLevel(rssi, 101).coerceIn(0, 100)
}
