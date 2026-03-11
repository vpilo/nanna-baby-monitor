package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.StreamingAudioFlow

interface StreamingAudioRepository {
    val chunks: StreamingAudioFlow
}
