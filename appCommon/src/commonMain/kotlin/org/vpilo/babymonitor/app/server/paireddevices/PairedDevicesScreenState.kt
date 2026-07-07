package org.vpilo.babymonitor.app.server.paireddevices

import org.vpilo.babymonitor.settings.model.repository.PairedClient

data class PairedDevicesScreenState(
    val clients: List<PairedClient> = emptyList(),
)
