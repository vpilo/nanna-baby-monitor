package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.Device

internal class ServerSelectionDataSource {
    private val collector = MutableStateFlow<Device.Server?>(null)
    val server: StateFlow<Device.Server?> = collector.asStateFlow()

    fun set(server: Device.Server?) {
        collector.value = server
    }
}
