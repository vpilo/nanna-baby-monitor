package org.vpilo.babymonitor.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_UPDATE_INTERVAL
import oshi.SystemInfo
import oshi.hardware.NetworkIF

internal fun getIsInternetAvailableFlow(): Flow<Boolean> =
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
