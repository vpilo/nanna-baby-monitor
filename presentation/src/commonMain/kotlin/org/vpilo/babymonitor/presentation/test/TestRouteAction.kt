package org.vpilo.babymonitor.presentation.test

sealed interface TestRouteAction {
    data class TestChanged(val something: String) : TestRouteAction
}
