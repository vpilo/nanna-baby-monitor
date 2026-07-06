package org.vpilo.babymonitor.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.data.DefaultDeviceStateRepository.Companion.TAG
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL
import oshi.SystemInfo

internal fun getBatteryLevelFlow(): Flow<Int> =
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
