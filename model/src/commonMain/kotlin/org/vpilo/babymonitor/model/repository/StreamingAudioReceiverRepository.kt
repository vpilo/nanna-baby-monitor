package org.vpilo.babymonitor.model.repository

/**
 * Repository that receives encoded audio chunks, decodes them, and plays the audio.
 * No output flow — audio is played directly to the system audio output.
 */
interface StreamingAudioReceiverRepository {
    fun startPlayback()
    fun stopPlayback()
}
