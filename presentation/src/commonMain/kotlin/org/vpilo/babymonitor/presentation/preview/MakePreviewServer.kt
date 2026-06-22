package org.vpilo.babymonitor.presentation.preview

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

fun makePreviewServer(
    name: String,
    isLocal: Boolean = true,
): Device.Server =
    if (isLocal) {
        Device.LocalServer(id = DeviceId.random(), name = name, addresses = emptySet())
    } else {
        Device.RemoteServer(id = DeviceId.random(), name = name, relayHost = "host")
    }
