package org.vpilo.babymonitor.presentation.server

sealed interface ServerPreviewAction {
    data class TestChanged(val something: String) : ServerPreviewAction
}
