package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

interface LocalClientDeviceRepository {
    val localDevice: Flow<Device.Client>
}
