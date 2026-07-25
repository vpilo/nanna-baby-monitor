package org.vpilo.babymonitor.network.internal.discovery.ktx

import org.vpilo.babymonitor.model.Device

internal fun Device.toAttributes(): Map<String, String> =
    mapOf(
        DEVICE_ATTRIBUTE_VERSION to DEVICE_ATTRIBUTE_SCHEMA_VERSION,
        DEVICE_ATTRIBUTE_NAME to name,
        DEVICE_ATTRIBUTE_TYPE to if (this is Device.LocalServer) DEVICE_TYPE_SERVER else DEVICE_TYPE_CLIENT,
    )
