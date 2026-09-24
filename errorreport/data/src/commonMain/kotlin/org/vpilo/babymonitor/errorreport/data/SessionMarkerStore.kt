package org.vpilo.babymonitor.errorreport.data

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.errorreport.model.ErrorRecorder
import org.vpilo.babymonitor.errorreport.model.SessionMarker
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps the current session's marker up to date.
 * The marker is written synchronously and atomically, so it is still accurate when the app dies.
 */
internal class SessionMarkerStore {
    private var session: SessionFiles? = null

    private var pid: Long = 0

    @Synchronized
    fun open(
        session: SessionFiles,
        pid: Long,
    ) {
        this.session = session
        this.pid = pid
        write(session, SessionMarker(active = false, pid = pid))
    }

    @Synchronized
    fun update(active: Boolean) {
        val session = session ?: return
        // Deleted on a clean shutdown; recreating it would leave an orphan behind.
        if (!ErrorRecorder.isRecording) return
        write(session, SessionMarker(active = active, pid = pid))
    }

    private fun write(
        session: SessionFiles,
        marker: SessionMarker,
    ) {
        runCatching {
            // The logs directory may be deleted while recording, e.g. by clearing the app's cache on Android.
            session.markerTemp.parentFile?.mkdirs()
            session.markerTemp.writeText("$KEY_ACTIVE=${marker.active}\n$KEY_PID=${marker.pid}\n")
            // Unlike File.renameTo(), this also replaces an existing file on Windows.
            Files.move(
                session.markerTemp.toPath(),
                session.marker.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure {
            Logger.w(TAG, it) { "Unable to write the session marker" }
        }
    }

    companion object {
        private val TAG = SessionMarkerStore::class
        private const val KEY_ACTIVE = "active"
        private const val KEY_PID = "pid"

        fun readMarkerOrNull(file: File): SessionMarker? {
            val values =
                runCatching { file.readLines() }
                    .getOrNull()
                    ?.associate { it.substringBefore('=') to it.substringAfter('=') }
                    ?: return null
            return SessionMarker(
                active = values[KEY_ACTIVE]?.toBooleanStrictOrNull() ?: return null,
                pid = values[KEY_PID]?.toLongOrNull() ?: return null,
            )
        }
    }
}
