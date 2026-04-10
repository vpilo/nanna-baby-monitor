package org.vpilo.babymonitor.app.menu

sealed interface MenuScreenEffect {
    data class Tbd(
        val tbd: Boolean,
    ) : MenuScreenEffect
}
