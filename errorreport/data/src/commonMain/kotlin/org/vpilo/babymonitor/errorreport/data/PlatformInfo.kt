package org.vpilo.babymonitor.errorreport.data

/**
 * Device and OS details to include in an error report.
 */
internal expect fun retrievePlatformMetadata(): Map<String, String>

internal expect fun currentProcessId(): Long
