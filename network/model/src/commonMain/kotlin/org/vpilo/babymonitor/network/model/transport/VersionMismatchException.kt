package org.vpilo.babymonitor.network.model.transport

class VersionMismatchException(
    val localVersion: Int,
    val remoteVersion: Int,
) : Exception("Relay protocol version mismatch: local $localVersion, remote $remoteVersion") {

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
        get() = "Relay protocol version mismatch: $mismatch (local $localVersion, remote $remoteVersion)"
}
