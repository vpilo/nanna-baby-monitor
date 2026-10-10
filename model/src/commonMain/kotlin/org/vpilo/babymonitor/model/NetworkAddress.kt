package org.vpilo.babymonitor.model

import org.vpilo.babymonitor.common.BuildInfo

@JvmInline
value class NetworkAddress(val address: String) {
    override fun toString(): String =
        "@${if (BuildInfo.IS_DEBUG) address else address.hashCode()}"
}
