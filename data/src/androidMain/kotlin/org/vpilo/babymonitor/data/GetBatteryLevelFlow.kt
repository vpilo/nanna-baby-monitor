package org.vpilo.babymonitor.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL

internal fun getBatteryLevelFlow(context: Context): Flow<Int> =
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
    }
