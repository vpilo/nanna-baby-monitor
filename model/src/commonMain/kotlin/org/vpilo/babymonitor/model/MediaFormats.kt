package org.vpilo.babymonitor.model

object MediaFormats {

    object Audio {
        const val SAMPLE_RATE = 48_000 // native sample rate for Opus
        const val SAMPLE_SIZE_BITS = 16
        const val CHANNELS = 1
        const val SIGNED = true
        const val BIG_ENDIAN = false

        const val BIT_RATE = 64_000 // 64 kbps Opus (voice-optimised, equivalent quality to 128 kbps AAC)

        const val FRAME_DURATION_MS = 20 // Milliseconds per frame
    }

    object Video {
        const val BIT_RATE = 2_000_000
        const val FRAME_RATE = 30

        const val KEY_FRAME_INTERVAL_SECONDS = 1
    }

    object BufferSizes {
        /**
         * Maximum amount of unprocessed frames to keep queued before starting to drop older ones.
         */
        const val MAX_FRAME_BUFFER_SIZE: Int = 10

        /**
         * Maximum amount of unprocessed audio samples to keep queued before starting to drop older ones.
         */
        const val MAX_SAMPLE_BUFFER_SIZE: Int = 32

        /**
         * Maximum amount of compressed video streaming chunks to keep queued before starting to drop older ones.
         */
        const val MAX_VIDEO_STREAM_BUFFER_SIZE: Int = 16

    }
}
