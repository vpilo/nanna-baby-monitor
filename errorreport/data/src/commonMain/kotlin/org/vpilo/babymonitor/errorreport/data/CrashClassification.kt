package org.vpilo.babymonitor.errorreport.data

import org.jetbrains.annotations.VisibleForTesting
import org.vpilo.babymonitor.common.LogLevel
import org.vpilo.babymonitor.errorreport.model.SessionCrashReason
import org.vpilo.babymonitor.errorreport.model.SessionMarker
import java.io.File
import kotlin.time.Instant

/**
 * Why the OS recorded the death of a process, mirroring Android's `ApplicationExitInfo` reasons.
 * The [outcome] indicates whether the death reason is worth reporting.
 */
internal enum class ExitReason(
    val outcome: Outcome,
) {
    CRASH(Outcome.REPORT),
    CRASH_NATIVE(Outcome.REPORT),
    ANR(Outcome.REPORT),
    LOW_MEMORY(Outcome.REPORT_IF_ACTIVE),
    SIGNALED(Outcome.REPORT_IF_ACTIVE),
    EXCESSIVE_RESOURCE_USAGE(Outcome.REPORT_IF_ACTIVE),
    DEPENDENCY_DIED(Outcome.REPORT_IF_ACTIVE),
    FREEZER(Outcome.REPORT_IF_ACTIVE),
    INITIALIZATION_FAILURE(Outcome.REPORT_IF_ACTIVE),
    OTHER(Outcome.REPORT_IF_ACTIVE),
    UNKNOWN(Outcome.REPORT_IF_ACTIVE),
    EXIT_SELF(Outcome.DISCARD),
    USER_REQUESTED(Outcome.DISCARD),
    USER_STOPPED(Outcome.DISCARD),
    PERMISSION_CHANGE(Outcome.DISCARD),
    PACKAGE_STATE_CHANGE(Outcome.DISCARD),
    PACKAGE_UPDATED(Outcome.DISCARD),
    ;

    enum class Outcome {
        REPORT,
        REPORT_IF_ACTIVE,
        DISCARD,
    }
}

internal data class ClassifiedCrash(
    val reason: SessionCrashReason,
    val processExitRecord: ProcessExitRecord?,
)

/**
 * Decide whether the given [sessionFiles] logs ended with a failure.
 *
 * An OS exit record for the session is authoritative when there is one. Without it, a log ending with an error means an uncaught
 * throwable, and otherwise dying while streaming is abnormal.
 *
 * @return a [ClassifiedCrash], or null if the given session ended successfully.
 */
internal fun getClassifiedCrashOrNull(
    sessionFiles: SessionFiles,
    processExitRecords: List<ProcessExitRecord>,
): ClassifiedCrash? {
    val marker = SessionMarkerStore.readMarkerOrNull(sessionFiles.marker)
    val isSessionActive = marker?.active == true

    val exitRecord = processExitRecords.findSession(marker, sessionFiles.startMillis)
    if (exitRecord != null) {
        val isReported =
            when (exitRecord.reason.outcome) {
                ExitReason.Outcome.REPORT -> true
                ExitReason.Outcome.REPORT_IF_ACTIVE -> isSessionActive
                ExitReason.Outcome.DISCARD -> false
            }
        return if (isReported) ClassifiedCrash(exitRecord.toSessionCrashReason(isSessionActive), exitRecord) else null
    }

    val lastLogLevel = readLastLogLevel(sessionFiles.log)
    return when {
        lastLogLevel == LogLevel.ERROR -> ClassifiedCrash(SessionCrashReason.UncaughtException, null)
        isSessionActive -> ClassifiedCrash(SessionCrashReason.AbnormalSession, null)
        else -> null
    }
}

private fun List<ProcessExitRecord>.findSession(
    marker: SessionMarker?,
    sessionStartMillis: Long?,
): ProcessExitRecord? {
    val candidates = filter { sessionStartMillis == null || it.timestampMillis >= sessionStartMillis }
    return if (marker != null) {
        candidates.firstOrNull { it.pid == marker.pid }
    } else {
        sessionStartMillis ?: return null
        candidates.minByOrNull { it.timestampMillis }
    }
}

private fun ProcessExitRecord.toSessionCrashReason(isSessionActive: Boolean): SessionCrashReason =
    SessionCrashReason.KilledByOs(
        reasonCode = reasonCode,
        reasonName = reason.name,
        description = description,
        importance = importance,
        timestamp = Instant.fromEpochMilliseconds(timestampMillis),
        sessionActive = isSessionActive,
    )

@VisibleForTesting
internal fun readLastLogLevel(logFile: File): LogLevel? = if (logFile.exists()) logFile.useLines { readLastLogLevel(it) } else null

/**
 * Find the level of the last log entry.
 * Each entry starts with `[elapsed time][LEVEL][logging tag]`.
 */
@VisibleForTesting
internal fun readLastLogLevel(lines: Sequence<String>): LogLevel? =
    lines
        .filter { it.startsWith('[') }
        .mapNotNull { line ->
            val token = line.substringAfter("][", missingDelimiterValue = "").substringBefore(']')
            LogLevel.entries.firstOrNull { it.name == token }
        }.lastOrNull()
