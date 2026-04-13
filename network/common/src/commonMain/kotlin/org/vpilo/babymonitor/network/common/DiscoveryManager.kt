package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow
import java.net.InetAddress

expect class DiscoveryManager {
    val discoveredServers: Flow<Set<InetAddress>>

    var state: DiscoveryManagerState
        private set

    fun registerService()

    fun unregisterService()

    fun startDiscovery()

    fun stopDiscovery()

    fun setDeviceName(name: String)
}
