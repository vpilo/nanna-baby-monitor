package org.vpilo.babymonitor.model

/**
 * Repository for raw audio recording.
 *
 * This repository provides uncompressed audio samples.
 */
interface AudioChunkRepository {
    val samples: AudioFlow
}
