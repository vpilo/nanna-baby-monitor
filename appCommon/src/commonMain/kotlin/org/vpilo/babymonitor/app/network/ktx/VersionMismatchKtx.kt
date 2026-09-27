package org.vpilo.babymonitor.app.network.ktx

import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.relay_version_mismatch_app_outdated
import babymonitor.appcommon.generated.resources.relay_version_mismatch_relay_outdated
import org.jetbrains.compose.resources.StringResource
import org.vpilo.babymonitor.network.model.transport.VersionMismatch

internal fun VersionMismatch.toMessageResource(): StringResource =
    when (this) {
        VersionMismatch.REMOTE_OUTDATED -> Res.string.relay_version_mismatch_relay_outdated
        VersionMismatch.LOCAL_OUTDATED -> Res.string.relay_version_mismatch_app_outdated
    }
