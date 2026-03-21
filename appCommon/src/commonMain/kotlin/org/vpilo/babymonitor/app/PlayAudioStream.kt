package org.vpilo.babymonitor.app

import org.vpilo.babymonitor.model.AudioFrameFlow

expect suspend fun playAudioStream(input: AudioFrameFlow)
