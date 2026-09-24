package org.vpilo.babymonitor.errorreport.data

import java.io.InputStream

/**
 * A process death recorded by the OS.
 */
internal data class ProcessExitRecord(
    val pid: Long,
    val timestampMillis: Long,
    val reason: ExitReason,
    val reasonCode: Int,
    val description: String?,
    val importance: Int,
    val openTrace: () -> InputStream?,
)
