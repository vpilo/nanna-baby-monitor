package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.AudioChunkRepository
import org.vpilo.babymonitor.model.AudioFlow
import org.vpilo.babymonitor.model.Configuration
import kotlin.reflect.KClass

actual class AudioRepository(
) : AudioChunkRepository,
    SharedResourceRepository<ByteArray>(
        bufferCapacity = Configuration.MAX_SAMPLE_BUFFER_SIZE,
    ) {
    override val samples: AudioFlow = collector.asSharedFlow()

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override val TAG: KClass<*> = AudioRepository::class
}
