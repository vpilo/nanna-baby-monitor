package org.vpilo.babymonitor.app.server.paireddevices

sealed interface PairedDevicesScreenAction {
    data class Revoke(
        val clientId: String,
    ) : PairedDevicesScreenAction
}
