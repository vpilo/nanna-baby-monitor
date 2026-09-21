package org.vpilo.babymonitor.network.model

/**
 * Configuration used to reach a relay server.
 */
data class RelayConfiguration(
    val host: String,
    val passphrase: String,
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && passphrase.isNotBlank()

    override fun toString(): String =
        "RelayConfiguration(<host ${host.hashCode()}>, ${if (passphrase.isBlank()) "no" else "with"} passphrase)"

    companion object {
        val NONE = RelayConfiguration(host = "", passphrase = "")
    }
}
