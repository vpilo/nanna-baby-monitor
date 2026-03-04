package org.vpilo.babymonitor.camera.data

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.camera.data.ktx.sizes
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrameData
import org.vpilo.babymonitor.model.CameraImageRotation
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

internal class DesktopCamera(
    private val videoFrames: MutableSharedFlow<CameraFrameData>,
    private val audioSamples: MutableSharedFlow<ByteArray>,
    webcamGetter: () -> Webcam = { Webcam.getDefault() },
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var videoCaptureJob: Job? = null

    private val frameCount = 10
    private var frameCounter = 0

    private val webcam: Webcam = webcamGetter()

    fun start() {
        if (videoCaptureJob?.isActive == true) {
            Logger.w(TAG) { "Camera is already running, ignoring start request." }
            return
        }

        webcam.setCustomViewSizes(*customResolutions)
        for (size in customResolutions) {
            webcam.setViewSize(size)
            if (webcam.open()) {
                Logger.d(TAG) { "Camera opened with ${size.sizes}" }
                break
            }
        }
        // Fall back to the default resolution if none of the good ones work.
        if (!webcam.isOpen) {
            webcam.setCustomViewSizes(null)
            check(webcam.open()) { "Failed to open webcam with any resolution." }
        }

        Logger.d(TAG) {
            "Camera supports resolutions: ${webcam.viewSizes.map { it.sizes }}, current ${webcam.viewSize.sizes}"
        }

        videoCaptureJob =
            CoroutineScope(coroutineDispatcher)
                .launch {
                    while (isActive && webcam.isOpen) {
                        if (!webcam.isImageNew) {
                            delay(10.milliseconds)
                            continue
                        }
                        webcam.getImage()
                            ?.let { image ->
                                CameraFrameData(
                                    width = image.width,
                                    height = image.height,
                                    rotation = CameraImageRotation.ROTATION_0,
                                    timestamp = Clock.System.now(),
                                    data = image.convertToRgba(),
                                )
                                    .also { frame -> videoFrames.tryEmit(frame) }

                                if (++frameCounter % frameCount == 0) {
                                    frameCounter = 0
                                    Logger.d(TAG) {
                                        "FPS: ${"%.02f".format(webcam.fps)} with ${image.sizes} image of type ${image.type}"
                                    }
                                }
                            }
                            ?: run {
                                Logger.w(TAG) { "Failed to capture image" }
                                delay(100.milliseconds)
                            }
                    }

                    delay(1.milliseconds)
                }
                .also {
                    it.invokeOnCompletion { ex ->
                        if (ex == null || ex is CancellationException) {
                            Logger.d(TAG) { "Camera stopped" }
                        } else {
                            Logger.w(TAG, ex) { "Camera failed!" }
                        }
                        webcam.close()
                        videoCaptureJob = null
                    }
                }
        Logger.i(TAG) { "Started camera" }
    }

    fun stop() {
        if (videoCaptureJob == null) {
            Logger.w(TAG) { "Camera was not running anymore!" }
            return
        }
        Logger.i(TAG) { "Stopping camera" }
        videoCaptureJob?.cancel()
        videoCaptureJob = null
    }

    fun isStarted() = videoCaptureJob != null

    private fun BufferedImage.convertToRgba(): ByteArray {
        // getRGB returns ARGB.
        val argbData = getRGB(0, 0, width, height, null, 0, width)
        return ByteArray(argbData.size * 4)
            .also { bytes ->
                for (idx in argbData.indices) {
                    val argb = argbData[idx]
                    val offset = idx * 4
                    // In order, R, G, B, A.
                    bytes[offset] = (argb shr 16 and 0xFF).toByte()
                    bytes[offset + 1] = (argb shr 8 and 0xFF).toByte()
                    bytes[offset + 2] = (argb and 0xFF).toByte()
                    bytes[offset + 3] = (argb shr 24 and 0xFF).toByte()
                }
            }
    }

    private companion object {
        private val TAG = DesktopCamera::class

        private val customResolutions = arrayOf<Dimension>(
            WebcamResolution.UHD4K.size,
            WebcamResolution.WUXGA.size,
            WebcamResolution.FHD.size,
            WebcamResolution.UXGA.size,
            WebcamResolution.HDP.size,
            WebcamResolution.SXGA.size,
            WebcamResolution.HD.size,
            WebcamResolution.XGA.size,
        )
    }
}
