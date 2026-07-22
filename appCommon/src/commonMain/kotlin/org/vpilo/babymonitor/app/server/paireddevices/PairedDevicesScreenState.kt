package org.vpilo.babymonitor.app.server.paireddevices

import org.vpilo.babymonitor.model.Device

data class PairedDevicesScreenState(
    val devices: List<Device> = emptyList(),
)
