package org.vpilo.babymonitor.build

import java.io.Serializable

/** Version information shared across every build script. [Serializable] so it survives the config cache. */
data class VersionInfo(
    val versionName: String,
    val versionCore: String,
    val versionCode: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
