package org.vpilo.babymonitor.app

import org.vpilo.babymonitor.errorreport.model.ErrorRecorder
import org.vpilo.babymonitor.settings.model.getSettingsDir
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

fun onApplicationStart() {
    val logsDir =
        File(getSettingsDir(), "logs")
            .also { if (!it.exists()) it.mkdirs() }
    ErrorRecorder.start(File(logsDir, getCurrentSessionLogFileName()))
}

fun onApplicationStop() {
    ErrorRecorder.stop()
}

private fun getCurrentSessionLogFileName(): String = "session_${Clock.System.now().toEpochMilliseconds()}.log"
