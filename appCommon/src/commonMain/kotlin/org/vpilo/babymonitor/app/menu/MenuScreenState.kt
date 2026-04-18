package org.vpilo.babymonitor.app.menu

import org.vpilo.babymonitor.model.AppRole

data class MenuScreenState(
    val currentRole: AppRole = AppRole.UNDECIDED,
)
