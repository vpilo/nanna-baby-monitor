package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.network.common.Server

internal class ServerSelectionDataSource {
    private val collector = MutableStateFlow<Server?>(null)
    val server: StateFlow<Server?> = collector.asStateFlow()

    var lastServerId: ServerId? = null
        private set

    fun set(server: Server?) {
        collector.value = server
        server?.let { lastServerId = it.id }
    }
}
