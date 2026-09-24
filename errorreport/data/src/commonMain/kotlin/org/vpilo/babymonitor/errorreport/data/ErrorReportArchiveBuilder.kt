package org.vpilo.babymonitor.errorreport.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.vpilo.babymonitor.common.BuildInfo
import org.vpilo.babymonitor.errorreport.model.SessionCrashReason
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.settings.model.getCacheDir
import org.vpilo.babymonitor.settings.model.getCurrentPlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the zip file of an error report, in memory.
 */
internal class ErrorReportArchiveBuilder(
    private val appRole: Flow<AppRole>,
) {
    /**
     * Write a report to an archive from [session] files with failure information taken from [crash].
     * The caller deletes it when done.
     */
    suspend fun build(
        session: SessionFiles,
        crash: ClassifiedCrash,
    ): File {
        val archive = makeArchive(session, crash)
        val dir = getReportStagingDir().apply { mkdirs() }
        return File(dir, archive.fileName).apply {
            writeBytes(archive.content)
            // In case the app dies before the report is deleted.
            deleteOnExit()
        }
    }

    private suspend fun makeArchive(
        session: SessionFiles,
        crash: ClassifiedCrash,
    ): Archive {
        val metadata = describeMetadata()
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_LOG))
            session.log.inputStream().use { it.copyTo(zip) }

            zip.putNextEntry(ZipEntry(ENTRY_METADATA))
            zip.write(metadata.toByteArray())

            zip.putNextEntry(ZipEntry(ENTRY_REASON))
            zip.write(describe(crash.reason).toByteArray())

            crash.processExitRecord?.openTrace?.invoke()?.use { trace ->
                zip.putNextEntry(ZipEntry(ENTRY_TRACE))
                trace.copyTo(zip)
            }
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN))
        return Archive(fileName = "nanna-baby-monitor-report-$timestamp.zip", content = buffer.toByteArray())
    }

    private suspend fun describeMetadata(): String {
        val metadata =
            mapOf(
                "version" to BuildInfo.VERSION,
                "versionCode" to BuildInfo.VERSION_CODE.toString(),
                "versionCore" to BuildInfo.VERSION_CORE,
                "debug" to BuildInfo.IS_DEBUG.toString(),
                "platform" to getCurrentPlatform().name,
                "role" to appRole.first().name,
            ) + retrievePlatformMetadata()
        return metadata.toLines()
    }

    internal class Archive(
        val fileName: String,
        val content: ByteArray,
    )

    // A directory where a report file can be picked up by a mail client.
    private fun getReportStagingDir(): File = File(getCacheDir(), REPORT_STAGING_DIR_NAME)

    companion object {
        private const val ENTRY_LOG = "session.log"
        private const val ENTRY_METADATA = "metadata.txt"
        private const val ENTRY_REASON = "reason.txt"
        private const val ENTRY_TRACE = "trace.txt"
        private const val FILE_NAME_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"

        // Must match the <cache-path> exposed by the app's FileProvider.
        private const val REPORT_STAGING_DIR_NAME = "error-reports"

        fun describe(reason: SessionCrashReason): String =
            when (reason) {
                is SessionCrashReason.KilledByOs -> {
                    mapOf(
                        "reason" to "OsKill: ${reason.reasonCode} (${reason.reasonName})",
                        "description" to reason.description.orEmpty(),
                        "importance" to reason.importance.toString(),
                        "timestamp" to reason.timestamp.toString(),
                        "sessionActive" to reason.sessionActive.toString(),
                    )
                }

                SessionCrashReason.UncaughtException -> {
                    mapOf("reason" to "UncaughtException")
                }

                SessionCrashReason.AbnormalSession -> {
                    mapOf("reason" to "AbnormalSession")
                }
            }.toLines()

        private fun Map<String, String>.toLines(): String =
            entries.joinToString(separator = "\n", postfix = "\n") { "${it.key}: ${it.value}" }
    }
}
