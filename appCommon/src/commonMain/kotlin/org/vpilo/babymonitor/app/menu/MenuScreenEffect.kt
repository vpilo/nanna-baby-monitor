package org.vpilo.babymonitor.app.menu

sealed interface MenuScreenEffect {
    data object Disconnected : MenuScreenEffect
}
