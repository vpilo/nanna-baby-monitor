package org.vpilo.babymonitor.errorreport.model

/**
 * State of a session which the log cannot hold reliably, kept up to date while the session runs.
 */
data class SessionMarker(
    /**
     * Whether the app was streaming, so a death at this point is unexpected.
     */
    val active: Boolean,
    val pid: Long,
)
