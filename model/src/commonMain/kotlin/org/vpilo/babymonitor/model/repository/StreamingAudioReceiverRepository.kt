package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.AudioFrameFlow

/**
 * Repository that provides decoded audio chunks.
 */
interface StreamingAudioReceiverRepository {
    val chunks: AudioFrameFlow
}
