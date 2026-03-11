package org.vpilo.babymonitor.model

interface StreamingAudioRepository {
    val chunks: StreamingAudioFlow
}
