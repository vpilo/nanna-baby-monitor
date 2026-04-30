package org.vpilo.babymonitor.common.ktx

fun Throwable.prettify(): String = "${this::class.simpleName}: ${this.message ?: "No message"}"
