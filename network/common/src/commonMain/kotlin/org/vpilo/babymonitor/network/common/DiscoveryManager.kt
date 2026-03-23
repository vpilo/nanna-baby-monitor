package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow
import java.net.InetAddress

expect class DiscoveryManager {
    val discoveredServers: Flow<Set<InetAddress>>

    fun registerService()
    fun unregisterService()
    fun startDiscovery()
    fun stopDiscovery()
}
