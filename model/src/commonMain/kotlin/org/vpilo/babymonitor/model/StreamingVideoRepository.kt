package org.vpilo.babymonitor.model

interface StreamingVideoRepository {
    val chunks: StreamingVideoFlow
}
