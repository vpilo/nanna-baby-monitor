package org.vpilo.babymonitor.data.device

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL
import oshi.SystemInfo
import oshi.hardware.NetworkIF
import java.io.File

internal actual class DeviceStateDataSource actual constructor() {
    actual val batteryLevel: Flow<Int> =
        flow {
            while (true) {
                val powerSources = SystemInfo().hardware.powerSources
                if (powerSources.isEmpty()) {
                    Logger.i(TAG) { "No power sources found." }
                    emit(DEVICE_STATE_DATA_UNAVAILABLE)
                } else {
                    val remainingCapacity = (powerSources[0].remainingCapacityPercent * 100).toInt()
                    if (remainingCapacity < 0) {
                        emit(DEVICE_STATE_DATA_UNAVAILABLE)
                    } else {
                        emit(remainingCapacity)
                    }
                }

                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }

    actual val signalLevel: Flow<Int> =
        flow {
            while (true) {
                if (!PROC_NET_WIRELESS.exists()) {
                    Logger.i(TAG) { "Wireless info file not found: $PROC_NET_WIRELESS" }
                    emit(DEVICE_STATE_DATA_UNAVAILABLE)
                } else {
                    val quality = readWifiSignalQuality()
                    emit(quality)
                }
                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }

    actual val isInternetAvailable: Flow<Boolean> =
        flow {
            var previousAddresses: Set<String>? = null
            while (true) {
                val activeAddresses =
                    SystemInfo()
                        .hardware.networkIFs
                        .flatMap { nif ->
                            nif.updateAttributes()
                            if (nif.ifOperStatus == NetworkIF.IfOperStatus.UP) {
                                nif.iPv4addr.toList() + nif.iPv6addr.toList()
                            } else {
                                emptyList()
                            }
                        }.toSet()

                if (activeAddresses != previousAddresses) {
                    emit(activeAddresses.isNotEmpty())
                    previousAddresses = activeAddresses
                }
                delay(DEVICE_STATE_UPDATE_INTERVAL)
            }
        }

    @Suppress("ReturnCount")
    private fun readWifiSignalQuality(): Int {
        val lines = PROC_NET_WIRELESS.readLines()
        // First two lines are headers; data lines start at index 2
        val dataLine = lines.getOrNull(2) ?: return DEVICE_STATE_DATA_UNAVAILABLE
        // Format: "wlp0s20f3: 0000   48.  -62.  -256   ..."
        // Fields after the colon: status, link, level, noise, ...
        val fields = dataLine.substringAfter(":").trim().split("\\s+".toRegex())
        val linkStr = fields.getOrNull(1) ?: return DEVICE_STATE_DATA_UNAVAILABLE
        val linkQuality = linkStr.trimEnd('.').toIntOrNull() ?: 100
        return (linkQuality * 100 / MAX_LINK_QUALITY).coerceIn(0, 100)
    }

    private companion object {
        private val TAG = DeviceStateDataSource::class

        private val PROC_NET_WIRELESS = File("/proc/net/wireless")
        private const val MAX_LINK_QUALITY = 70
    }
}
