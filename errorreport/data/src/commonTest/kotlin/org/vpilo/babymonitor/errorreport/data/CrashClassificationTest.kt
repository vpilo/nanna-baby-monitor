package org.vpilo.babymonitor.errorreport.data

import org.vpilo.babymonitor.common.LogLevel
import org.vpilo.babymonitor.errorreport.model.SessionCrashReason
import org.vpilo.babymonitor.errorreport.model.SessionMarker
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CrashClassificationTest {
    private val sessionStart = 1_000_000L
    private val pid = 42L

    private fun exitRecord(
        reason: ExitReason,
        pid: Long = this.pid,
        timestampMillis: Long = sessionStart + 60_000,
    ) = ProcessExitRecord(
        pid = pid,
        timestampMillis = timestampMillis,
        reason = reason,
        reasonCode = reason.ordinal,
        description = null,
        importance = 0,
        openTrace = { null },
    )

    private fun classify(
        processExitRecords: List<ProcessExitRecord> = emptyList(),
        lastLogLevel: LogLevel? = LogLevel.INFO,
        marker: SessionMarker? = SessionMarker(active = false, pid = pid),
    ): ClassifiedCrash? {
        val tempDir = createTempDirectory().toFile()
        try {
            val sessionFiles = SessionFiles(tempDir, "session_$sessionStart", sessionStart)

            lastLogLevel?.let {
                sessionFiles.log.writeText("[00:00:01.000][$it][TestTag] Test log entry\n")
            }
            marker?.let {
                sessionFiles.marker.writeText("active=${it.active}\npid=${it.pid}\n")
            }

            return getClassifiedCrashOrNull(sessionFiles, processExitRecords)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reportedReasonsAreReportedWhateverTheSessionState() {
        listOf(ExitReason.CRASH, ExitReason.CRASH_NATIVE, ExitReason.ANR).forEach { expectedReason ->
            val record = exitRecord(expectedReason)
            val crash = assertNotNull(classify(listOf(record), marker = SessionMarker(active = false, pid = pid)), "$expectedReason")
            val reason = assertIs<SessionCrashReason.KilledByOs>(crash.reason, "$expectedReason")
            assertEquals(expectedReason.name, reason.reasonName)
            assertEquals(false, reason.sessionActive)
            assertSame(record, crash.processExitRecord)
        }
    }

    @Test
    fun conditionalReasonsAreReportedOnlyWhenActive() {
        ExitReason.entries.filter { it.outcome == ExitReason.Outcome.REPORT_IF_ACTIVE }.forEach { reason ->
            val active = classify(listOf(exitRecord(reason)), marker = SessionMarker(active = true, pid = pid))
            assertIs<SessionCrashReason.KilledByOs>(active?.reason, "$reason")
            assertNull(classify(listOf(exitRecord(reason)), marker = SessionMarker(active = false, pid = pid)), "$reason")
        }
    }

    @Test
    fun discardedReasonsAreNeverReported() {
        ExitReason.entries.filter { it.outcome == ExitReason.Outcome.DISCARD }.forEach { reason ->
            assertNull(classify(listOf(exitRecord(reason)), marker = SessionMarker(active = true, pid = pid)), "$reason")
        }
    }

    @Test
    fun exitRecordOverridesTheLogEndingWithAnError() {
        assertNull(classify(listOf(exitRecord(ExitReason.USER_REQUESTED)), lastLogLevel = LogLevel.ERROR))
    }

    @Test
    fun exitRecordIsMatchedByPid() {
        val otherProcess = exitRecord(ExitReason.CRASH, pid = pid + 1)
        val ownProcess = exitRecord(ExitReason.USER_REQUESTED)
        assertNull(classify(listOf(otherProcess, ownProcess)))
    }

    @Test
    fun exitRecordOlderThanTheSessionIsIgnoredDespiteMatchingPid() {
        val reusedPid = exitRecord(ExitReason.CRASH, timestampMillis = sessionStart - 1)
        assertNull(classify(listOf(reusedPid)))
    }

    @Test
    fun withoutMarkerTheEarliestExitRecordAfterTheSessionStartIsMatched() {
        val records =
            listOf(
                exitRecord(ExitReason.USER_REQUESTED, pid = 2, timestampMillis = sessionStart + 2_000),
                exitRecord(ExitReason.CRASH, pid = 1, timestampMillis = sessionStart + 1_000),
                exitRecord(ExitReason.ANR, pid = 3, timestampMillis = sessionStart - 1_000),
            )
        val reason = assertIs<SessionCrashReason.KilledByOs>(classify(records, marker = null)?.reason)
        assertEquals(ExitReason.CRASH.name, reason.reasonName)
    }

    @Test
    fun withoutExitRecordTheLogEndingWithAnErrorIsAnUncaughtException() {
        assertEquals(SessionCrashReason.UncaughtException, classify(lastLogLevel = LogLevel.ERROR)?.reason)
        assertEquals(SessionCrashReason.UncaughtException, classify(lastLogLevel = LogLevel.ERROR, marker = null)?.reason)
    }

    @Test
    fun withoutExitRecordDyingWhileActiveIsAbnormal() {
        assertEquals(SessionCrashReason.AbnormalSession, classify(marker = SessionMarker(active = true, pid = pid))?.reason)
    }

    @Test
    fun withoutExitRecordDyingWhileIdleIsNotReported() {
        assertNull(classify())
        assertNull(classify(lastLogLevel = null))
    }

    @Test
    fun withoutMarkerTheSessionIsConsideredIdle() {
        assertNull(classify(marker = null))
    }

    @Test
    fun lastLogLevelIgnoresStackTraceLines() {
        val lines =
            sequenceOf(
                "[00:00:01.000][INFO][Tag] Started",
                "[00:00:02.500][ERROR][ErrorRecorder] Uncaught exception on thread main, IllegalStateException",
                "java.lang.IllegalStateException: [00:00:03.000][INFO][Fake] Not a header",
                "\tat org.vpilo.babymonitor.Foo.bar(Foo.kt:1)",
                "Caused by: java.lang.RuntimeException",
                "\t... 3 more",
            )
        assertEquals(LogLevel.ERROR, readLastLogLevel(lines))
    }

    @Test
    fun lastLogLevelIsTheLevelOfTheLastEntry() {
        val lines =
            """
            [00:00:01.000][ERROR][Tag] Failed
            [100:00:02.000][DEBUG][Tag] Recovered
            """.trimIndent().lineSequence()
        assertEquals(LogLevel.DEBUG, readLastLogLevel(lines))
    }

    @Test
    fun lastLogLevelIgnoresMalformedLines() {
        assertNull(readLastLogLevel(sequenceOf("", "[broken", "[00:00:01.000][NOPE][Tag] Message")))
    }
}
