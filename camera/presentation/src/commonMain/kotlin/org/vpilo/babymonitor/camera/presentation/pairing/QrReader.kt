package org.vpilo.babymonitor.camera.presentation.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class QrReader {
    private val reader =
        MultiFormatReader().apply {
            val hints =
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.CHARACTER_SET to "UTF-8",
                    DecodeHintType.TRY_HARDER to true,
                )
            setHints(hints)
        }

    private val lastResult = MutableStateFlow<String?>(null)

    private var lastReadInstant: Instant = Instant.DISTANT_PAST

    // To avoid read the same QR code multiple times or emitting null immediately after a successful read,
    // the result is debounced by stopping the reader for a while.
    val scannedDataFlow: Flow<String?> =
        lastResult
            .onEach {
                it ?: return@onEach
                lastReadInstant = Clock.System.now()
            }

    fun readFrame(frame: BinaryBitmap) {
        if (Clock.System.now() - lastReadInstant < READ_DEBOUNCE_DURATION) {
            return
        }

        try {
            reader
                .decodeWithState(frame)
                ?.let { result ->
                    lastResult.update { result.text }
                }
        } catch (_: NotFoundException) {
            // Nothing found or invalid QR.
            lastResult.update { null }
        } finally {
            reader.reset()
        }
    }

    private companion object {
        private val READ_DEBOUNCE_DURATION = 5.seconds
    }
}
