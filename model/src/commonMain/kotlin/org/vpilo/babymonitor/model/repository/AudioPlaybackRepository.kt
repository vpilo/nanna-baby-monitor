package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.AudioFrameFlow

interface AudioPlaybackRepository {
    suspend fun play(input: AudioFrameFlow)
}

