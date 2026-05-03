package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.repository.ServerId
import java.net.InetAddress

internal data class ConnectionTarget(
    val address: InetAddress,
    val serverId: ServerId,
)

internal class ConnectionTargetDataSource {
    private val collector = MutableStateFlow<ConnectionTarget?>(null)
    val target: StateFlow<ConnectionTarget?> = collector.asStateFlow()

    fun set(target: ConnectionTarget?) {
        collector.value = target
    }
}
