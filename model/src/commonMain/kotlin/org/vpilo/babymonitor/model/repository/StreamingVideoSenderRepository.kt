package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.StreamingVideoFlow

interface StreamingVideoSenderRepository {
    val chunks: StreamingVideoFlow

    fun start()

    fun stop()
}
