package org.vpilo.babymonitor.model

import org.vpilo.babymonitor.common.BuildInfo

@JvmInline
value class NetworkAddress(
    val address: String,
) {
    override fun toString(): String =
        when {
            address.isEmpty() -> "@<none>"
            BuildInfo.IS_DEBUG -> "@$address"
            else -> "@<${address.hashCode()}>"
        }

    companion object {
        val NONE = NetworkAddress("")
    }
}
