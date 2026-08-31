package org.vpilo.babymonitor.model.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository

/**
 * Use case that bridges the audio receiver (decoded chunks from the network)
 * with the audio playback device (speaker).
 *
 * Call [setPlaying] to start or stop playback.
 * The caller's [CoroutineScope] controls the playback lifetime - when cancelled, playback stops.
 */
class PlayReceivedAudioUseCase(
    private val audioReceiverRepository: StreamingAudioReceiverRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
) {
    private var playbackJob: Job? = null

    fun setPlaying(
        scope: CoroutineScope,
        start: Boolean,
    ) {
        val active = playbackJob?.isActive == true
        if (active == start) return
        if (start) {
            playbackJob =
                scope.launch {
                    audioPlaybackRepository.play(audioReceiverRepository.chunks)
                }
        } else {
            playbackJob?.cancel()
            playbackJob = null
        }
    }
}
