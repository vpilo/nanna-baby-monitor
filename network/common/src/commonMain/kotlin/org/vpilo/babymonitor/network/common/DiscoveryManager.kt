package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow

expect class DiscoveryManager {
    val discoveredServers: Flow<Set<DiscoveredServer>>

    var state: DiscoveryManagerState
        private set

    fun registerService()

    fun unregisterService()

    fun startDiscovery()

    fun stopDiscovery()

    fun setDeviceName(name: String)
}
