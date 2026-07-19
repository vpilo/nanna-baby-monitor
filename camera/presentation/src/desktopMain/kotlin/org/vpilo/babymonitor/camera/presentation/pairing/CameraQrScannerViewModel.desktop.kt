package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import com.google.zxing.BinaryBitmap
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@Stable
actual class CameraQrScannerViewModel internal constructor(
    webcamGetter: () -> Webcam,
) : AppViewModel<Unit, Unit, CameraQrScannerEffect>(initialState = Unit) {
    actual constructor() : this({ Webcam.getDefault() })

    private val _frames = MutableStateFlow<ImageBitmap?>(null)
    val frames: StateFlow<ImageBitmap?> = _frames.asStateFlow()

    private val qrReader = QrReader()

    private var cameraJob: Job? = null

    override fun SubscriptionScope.onSubscribed() {
        qrReader.scannedDataFlow.subscribe {
            it ?: return@subscribe
            CameraQrScannerEffect.QrScanned(it).sendEffect()
        }

        cameraJob?.cancel()
        cameraJob =
            vmScope.launch {
                bindToCamera()
            }
    }

    override suspend fun onUnsubscribed() {
        close()
    }

    private val webcam: Webcam = webcamGetter()

    private val mediumResolutions =
        arrayOf<Dimension>(
            WebcamResolution.HD.size,
            WebcamResolution.WXGA2.size,
            WebcamResolution.SXGA.size,
            WebcamResolution.XGA.size,
        )

    private suspend fun bindToCamera() {
        @Suppress("SpreadOperator")
        webcam.setCustomViewSizes(*mediumResolutions)
        for (size in mediumResolutions) {
            webcam.setViewSize(size)
            if (webcam.open()) {
                Logger.d(TAG) { "Camera started with ${size.width}x${size.height} resolution" }
                break
            }
        }

        if (!webcam.isOpen) {
            webcam.setCustomViewSizes(null)
            if (!webcam.open()) {
                Logger.w(TAG) { "Failed to open webcam with any resolution." }
                CameraQrScannerEffect.CameraError.sendEffect()
                return
            } else {
                Logger.d(TAG) { "Camera started with default resolution" }
            }
        }

        try {
            frameLoop()
        } catch (ex: CancellationException) {
            Logger.d(TAG) { "Camera stopped" }
            throw ex
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            Logger.w(TAG, ex) { "Camera failed!" }
            CameraQrScannerEffect.CameraError.sendEffect()
        } finally {
            close()
        }
    }

    private suspend fun frameLoop() {
        // 20fps
        val maxFrameTime = (1000L / 20L).milliseconds

        while (webcam.isOpen) {
            val frameTime =
                measureTime {
                    if (!webcam.isImageNew) {
                        delay(5.milliseconds)
                        return@measureTime
                    }
                    val captured: BufferedImage? = webcam.getImage()
                    if (captured == null) {
                        Logger.w(TAG) { "Failed to capture image" }
                        delay(100.milliseconds)
                    } else {
                        _frames.value = captured.toComposeImageBitmap()
                        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(captured)))
                        qrReader.readFrame(bitmap)
                    }
                }

            val diff = frameTime - maxFrameTime
            if (diff.isNegative()) {
                delay(maxFrameTime - frameTime)
            } else if (diff > 100.milliseconds) {
                Logger.w(TAG) { "Frame capture/analysis took too long: $frameTime" }
            }
        }
    }

    private fun close() {
        webcam
            .close()
            .also {
                Logger.w(TAG) { "Camera closed: $it" }
            }
        _frames.value = null

        cameraJob?.cancel()
        cameraJob = null
    }
}
