package org.vpilo.babymonitor.model.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository

/**
 * Use case that bridges the audio receiver (decoded chunks from the network)
 * with the audio playback device (speaker).
 *
 * Call [toggle] to start or stop playback. Observe [isPlaying] for the current state.
 * The caller's [CoroutineScope] controls the playback lifetime — when cancelled, playback stops.
 */
class PlayReceivedAudioUseCase(
    private val audioReceiverRepository: StreamingAudioReceiverRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
) {
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    fun toggle(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) {
            playbackJob?.cancel()
            playbackJob = null
        } else {
            playbackJob =
                scope.launch {
                    audioPlaybackRepository.play(audioReceiverRepository.chunks)
                }
            playbackJob?.invokeOnCompletion {
                _isPlaying.value = false
                playbackJob = null
            }
            _isPlaying.value = true
        }
    }
}
