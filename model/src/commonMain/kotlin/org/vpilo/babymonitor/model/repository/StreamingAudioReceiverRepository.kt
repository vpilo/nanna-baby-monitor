package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.AudioFrameFlow

/**
 * Repository that provides decoded audio chunks.
 */
interface StreamingAudioReceiverRepository {
    val chunks: AudioFrameFlow
    val isActive: Flow<Boolean>
}
