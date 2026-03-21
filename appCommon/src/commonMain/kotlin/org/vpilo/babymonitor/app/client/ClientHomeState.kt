package org.vpilo.babymonitor.app.client

import org.vpilo.babymonitor.model.repository.NetworkState

data class ClientHomeState(
    val networkState: NetworkState = NetworkState.Connecting,
)
