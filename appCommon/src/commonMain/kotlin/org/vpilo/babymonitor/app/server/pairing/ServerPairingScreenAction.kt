package org.vpilo.babymonitor.app.server.pairing

sealed interface ServerPairingScreenAction {
    data object Retry : ServerPairingScreenAction
}
