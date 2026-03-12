package org.vpilo.babymonitor.network.common

import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.charsets.Charset
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk

internal object Constants {
    const val COMMUNICATION_PORT = 47812

    const val SERVER_LISTEN_ADDRESS = "0.0.0.0"

    // Test, until network discovery is implemented.
    const val CLIENT_ADDRESS = "127.0.0.1"

    val ENCODING_CHARSET: Charset = Charset.defaultCharset()

    val TYPE_INFO_AUDIO_CHUNK = typeInfo<EncodedAudioStreamChunk>()

    val TYPE_INFO_VIDEO_CHUNK = typeInfo<EncodedVideoStreamChunk>()
}
