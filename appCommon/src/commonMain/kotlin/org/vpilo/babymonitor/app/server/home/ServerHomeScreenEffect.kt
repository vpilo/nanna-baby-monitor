package org.vpilo.babymonitor.app.server.home

import org.vpilo.babymonitor.network.model.transport.VersionMismatch

sealed interface ServerHomeScreenEffect {
    data class AnnounceRelayVersionMismatch(
        val mismatch: VersionMismatch,
    ) : ServerHomeScreenEffect
}
