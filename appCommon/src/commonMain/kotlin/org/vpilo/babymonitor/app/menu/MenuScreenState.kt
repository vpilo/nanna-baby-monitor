package org.vpilo.babymonitor.app.menu

import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.NetworkState
import java.net.InetAddress

data class MenuScreenState(
    val currentRole: AppRole = AppRole.UNDECIDED,
)
