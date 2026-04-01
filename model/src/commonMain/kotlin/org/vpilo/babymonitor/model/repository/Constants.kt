package org.vpilo.babymonitor.model.repository

import kotlin.time.Duration.Companion.minutes

/**
 * Used in [DeviceStateRepository] to indicate that the battery level or signal quality data is unavailable.
 */
const val DEVICE_STATE_DATA_UNAVAILABLE = -1

val DEVICE_STATE_UPDATE_INTERVAL = 1.minutes
