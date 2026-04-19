package org.vpilo.babymonitor.model.repository

data class ServerId(
    val name: String,
    val isLocalServer: Boolean = true,
)
