package org.vpilo.babymonitor.errorreport.data

import java.io.File

/**
 * The files of one session saved the [logsDir]. None of them needs to exist.
 */
internal data class SessionFiles(
    private val logsDir: File,
    private val baseName: String,
    val startMillis: Long?,
) {
    constructor(logsDir: File, baseName: String) : this(logsDir, baseName, baseName.removePrefix(PREFIX).toLongOrNull())

    /** Recorded logging for a session. */
    val log: File = File(logsDir, "$baseName.$LOG_EXTENSION")

    /** Session activity marker for a session. See [SessionMarkerStore]. */
    val marker: File = File(logsDir, "$baseName.$MARKER_EXTENSION")

    /** Temporary session activity marker for a session. See [SessionMarkerStore]. */
    val markerTemp: File = File(logsDir, "$baseName.$MARKER_TEMP_EXTENSION")

    /**
     * Delete the log first: the marker must outlive it.
     */
    fun delete() {
        log.delete()
        marker.delete()
        markerTemp.delete()
    }

    companion object {
        private const val PREFIX = "session_"
        private const val LOG_EXTENSION = "log"
        private const val MARKER_EXTENSION = "state"
        private const val MARKER_TEMP_EXTENSION = "tmp"

        fun create(
            logsDir: File,
            startMillis: Long,
        ): SessionFiles = SessionFiles(logsDir, "$PREFIX$startMillis", startMillis)

        /**
         * Find the sessions which left files behind, newest first.
         */
        fun findPreviousSessions(logsDir: File): List<SessionFiles> =
            logsDir
                .listFiles()
                .orEmpty()
                .asSequence()
                .filter { it.name.startsWith(PREFIX) }
                .map { it.nameWithoutExtension }
                .distinct()
                .map { SessionFiles(logsDir, it) }
                .toList()
    }
}
