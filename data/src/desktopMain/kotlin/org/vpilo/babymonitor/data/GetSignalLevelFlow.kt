package org.vpilo.babymonitor.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.data.DefaultDeviceStateRepository.Companion.TAG
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL
import java.io.File

private const val PROC_NET_WIRELESS = "/proc/net/wireless"
private const val MAX_LINK_QUALITY = 70

internal fun getSignalLevelFlow(): Flow<Int> =
    flow {
        val wirelessFile = File(PROC_NET_WIRELESS)
        if (!wirelessFile.exists()) {
            Logger.w(TAG) { "Wireless info file not found: $PROC_NET_WIRELESS" }
            emit(DEVICE_STATE_DATA_UNAVAILABLE)
            return@flow
        }

        while (true) {
            val quality = readWifiSignalQuality(wirelessFile)
            emit(quality)
            delay(DEVICE_STATE_UPDATE_INTERVAL)
        }
    }

@Suppress("ReturnCount")
private fun readWifiSignalQuality(wirelessFile: File): Int {
    val lines = wirelessFile.readLines()
    // First two lines are headers; data lines start at index 2
    val dataLine = lines.getOrNull(2) ?: return DEVICE_STATE_DATA_UNAVAILABLE
    // Format: "wlp0s20f3: 0000   48.  -62.  -256   ..."
    // Fields after the colon: status, link, level, noise, ...
    val fields = dataLine.substringAfter(":").trim().split("\\s+".toRegex())
    val linkStr = fields.getOrNull(1) ?: return DEVICE_STATE_DATA_UNAVAILABLE
    val linkQuality = linkStr.trimEnd('.').toIntOrNull() ?: 100
    return (linkQuality * 100 / MAX_LINK_QUALITY).coerceIn(0, 100)
}
