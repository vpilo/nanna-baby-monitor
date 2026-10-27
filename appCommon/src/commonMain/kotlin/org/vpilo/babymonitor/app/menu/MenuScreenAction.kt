package org.vpilo.babymonitor.app.menu

sealed interface MenuScreenAction {
    data object Disconnect : MenuScreenAction

    data object SendErrorReport : MenuScreenAction
}
