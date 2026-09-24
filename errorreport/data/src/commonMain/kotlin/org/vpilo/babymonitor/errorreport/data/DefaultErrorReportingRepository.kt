package org.vpilo.babymonitor.errorreport.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.BuildInfo
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.errorreport.model.ErrorRecorder
import org.vpilo.babymonitor.errorreport.model.ErrorReportingRepository
import org.vpilo.babymonitor.errorreport.model.PendingErrorReport
import org.vpilo.babymonitor.errorreport.model.ReportHandoff
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.settings.model.getCacheDir
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

internal class DefaultErrorReportingRepository(
    appRoleRepository: AppRoleRepository,
    coroutineContext: CoroutineContext,
) : ErrorReportingRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val archiver = ErrorReportArchiveBuilder(appRoleRepository.appRole)

    private val markerStore = SessionMarkerStore()

    private val pendingReportState = MutableStateFlow<PendingErrorReport?>(null)
    override val pendingReport: StateFlow<PendingErrorReport?> = pendingReportState.asStateFlow()

    private val mutex = Mutex()

    private var previousSession: SessionFiles? = null

    private var previousCrash: ClassifiedCrash? = null

    private var stagedReport: File? = null

    private var examinePreviousSessionJob: Job? = null

    @Synchronized
    override fun startNewSession() {
        if (ErrorRecorder.isRecording) return

        val logsDir = getLogsDir()
        // Saved before creating the new session's files, to not include them.
        val previousSessions = SessionFiles.findPreviousSessions(logsDir)

        val session = SessionFiles.create(logsDir, Clock.System.now().toEpochMilliseconds())
        markerStore.open(session, currentProcessId())
        ErrorRecorder.forwardSession(session.log)

        examinePreviousSessionJob?.cancel()
        examinePreviousSessionJob =
            scope.launch(Dispatchers.IO) {
                examinePreviousSessions(previousSessions)
            }
    }

    override suspend fun markSessionActive(active: Boolean) =
        withContext(Dispatchers.IO) {
            markerStore.update(active)
        }

    override suspend fun send(): Result<ReportHandoff> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val session = checkNotNull(previousSession) { "No pending report" }
                    val crash = checkNotNull(previousCrash) { "No pending report" }
                    val reportFile = archiver.build(session, crash)
                    stagedReport = reportFile
                    sendErrorReport(
                        report = reportFile,
                        subject = "Nanna Baby Monitor error report (${BuildInfo.VERSION})",
                        body = ErrorReportArchiveBuilder.describe(crash.reason),
                    )
                }
            }
        }

    override suspend fun discard() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                clearPreviousState()
            }
        }

    /**
     * Keep the newest previous session if it ended with a failure, and delete everything else.
     */
    private suspend fun examinePreviousSessions(previousSessions: List<SessionFiles>) =
        mutex.withLock {
            // A report pending from before a restart of the session is stale: stopping deleted its files.
            clearPreviousState()

            val newest =
                previousSessions
                    .sortedByDescending { it.startMillis ?: 0 }
                    .firstOrNull { it.log.exists() }
            previousSessions.filter { it != newest }.forEach { it.delete() }
            newest ?: return@withLock

            val crash =
                runCatching {
                    getClassifiedCrashOrNull(
                        sessionFiles = newest,
                        processExitRecords = readProcessExitRecords(),
                    )
                }.onFailure { Logger.w(TAG, it) { "Unable to examine the previous session" } }
                    .getOrNull()
            if (crash == null) {
                Logger.d(TAG) { "The previous session ended successfully" }
                newest.delete()
                return@withLock
            }

            Logger.i(TAG) { "The previous session ended with a failure: ${crash.reason}" }
            previousSession = newest
            previousCrash = crash
            pendingReportState.value = PendingErrorReport(crash.reason)
        }

    private fun clearPreviousState() {
        stagedReport?.delete()
        stagedReport = null
        previousSession?.delete()
        previousSession = null
        previousCrash = null
        pendingReportState.value = null
    }

    private fun getLogsDir(): File = File(getCacheDir(), "logs")

    private companion object {
        private val TAG = DefaultErrorReportingRepository::class
    }
}
