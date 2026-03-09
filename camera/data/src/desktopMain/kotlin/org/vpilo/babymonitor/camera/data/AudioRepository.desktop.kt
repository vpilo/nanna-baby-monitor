package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioChunkRepository
import org.vpilo.babymonitor.model.AudioFlow
import org.vpilo.babymonitor.model.Configuration
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.reflect.KClass

actual class AudioRepository(
) : AudioChunkRepository,
    SharedResourceRepository<ByteArray>(
        bufferCapacity = Configuration.MAX_SAMPLE_BUFFER_SIZE,
    ) {
    override val samples: AudioFlow = collector.asSharedFlow()

    private var targetLine: TargetDataLine? = null
    private var audioJob: Job? = null

    override fun start() {
        if (audioJob?.isActive == true) {
            Logger.w(TAG) { "Audio is already running, ignoring start request." }
            return
        }

        val format = AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN,
        )
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            Logger.e(TAG) { "Audio line not supported: $info" }
            return
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(format)
        line.start()
        targetLine = line
        Logger.d(TAG) { "Audio line opened: ${line.format}" }

        audioJob = coroutineScope.launch {
            val buffer = ByteArray(line.bufferSize / 2)
            while (isActive && line.isOpen) {
                val read = line.read(buffer, 0, buffer.size)
                if (read > 0) {
                    collector.tryEmit(buffer.copyOf(read))
                }
            }
        }.apply {
            invokeOnCompletion {
                line.stop()
                line.close()
                targetLine = null
                Logger.d(TAG) { "Audio line closed" }
            }
        }
    }

    override fun stop() {
        audioJob?.cancel()
        audioJob = null
    }

    override val TAG: KClass<*> = AudioRepository::class

    private companion object {
        const val SAMPLE_RATE = 44100f
        const val SAMPLE_SIZE_BITS = 16
        const val CHANNELS = 1
        const val SIGNED = true
        const val BIG_ENDIAN = false
    }
}
