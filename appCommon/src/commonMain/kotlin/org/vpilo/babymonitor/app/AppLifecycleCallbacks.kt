package org.vpilo.babymonitor.app

import org.vpilo.babymonitor.errorreport.model.ErrorRecorder

fun onApplicationStart() {
    ErrorRecorder.start()
}

fun onApplicationStop() {
    ErrorRecorder.stop()
}
