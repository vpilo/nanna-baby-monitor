package org.vpilo.babymonitor.network.model.transport

class VersionMismatchException(
    val localVersion: Int,
    val remoteVersion: Int,
    val isRelayConnection: Boolean,
) : Exception("Protocol version mismatch: local $localVersion, remote $remoteVersion (relay=$isRelayConnection)") {
    init {
        check(localVersion != remoteVersion) { "Versions must differ" }
    }

    val mismatch: VersionMismatch =
        if (remoteVersion < localVersion) {
            VersionMismatch.REMOTE_OUTDATED
        } else {
            VersionMismatch.LOCAL_OUTDATED
        }

    override val message: String
        get() = "Protocol version mismatch: $mismatch (local $localVersion, remote $remoteVersion, relay=$isRelayConnection)"
}
