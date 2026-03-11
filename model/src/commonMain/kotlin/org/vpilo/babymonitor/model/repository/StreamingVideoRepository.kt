package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.StreamingVideoFlow

interface StreamingVideoRepository {
    val chunks: StreamingVideoFlow
}
