package org.vpilo.babymonitor.network.common

import kotlinx.coroutines.flow.Flow

expect class DiscoveryManager {
    val discoveredServers: Flow<Set<DiscoveredServer>>

    val state: Flow<DiscoveryManagerState>

    fun registerService()

    fun unregisterService()

    fun startDiscovery()

    fun stopDiscovery()

    fun setDeviceName(name: String)
}
