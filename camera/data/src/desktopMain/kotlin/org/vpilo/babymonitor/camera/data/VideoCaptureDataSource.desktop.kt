package org.vpilo.babymonitor.camera.data

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.camera.data.ktx.sizes
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import java.awt.Dimension
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal actual class VideoCaptureDataSource(
    webcamGetter: () -> Webcam,
) : SharedResourceHolder<CameraFrame>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
    ) {
    actual constructor() : this(webcamGetter = { Webcam.getDefault() })

    actual val frames: CameraFrameFlow = collector.asSharedFlow()

    private var videoCaptureJob: Job? = null

    private var resolution: CameraResolution = CameraResolution.Medium

    private val webcam: Webcam = webcamGetter()

    override fun start() {
        if (videoCaptureJob?.isActive == true) {
            Logger.w(TAG) { "Camera is already running, ignoring start request." }
            return
        }

        val resolutions = resolutionsFor(resolution)
        @Suppress("SpreadOperator")
        webcam.setCustomViewSizes(*resolutions)
        for (size in resolutions) {
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
            coroutineScope
                .launch {
                    while (isActive && webcam.isOpen) {
                        if (!webcam.isImageNew) {
                            delay(10.milliseconds)
                            continue
                        }
                        webcam
                            .getImage()
                            ?.let { image -> collector.tryEmit(CameraFrame(image)) }
                            ?: run {
                                Logger.w(TAG) { "Failed to capture image" }
                                delay(100.milliseconds)
                            }
                    }

                    delay(1.milliseconds)
                }.apply {
                    invokeOnCompletion { ex ->
                        if (ex == null || ex is CancellationException) {
                            Logger.d(TAG) { "Camera stopped" }
                        } else {
                            Logger.w(TAG, ex) { "Camera failed!" }
                        }
                        webcam.close()
                        videoCaptureJob = null
                    }
                }
        Logger.i(TAG) { "Camera started" }
    }

    override fun stop() {
        videoCaptureJob?.cancel()
        videoCaptureJob = null
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (this.resolution == resolution) return
        this.resolution = resolution
        if (isActive) {
            Logger.i(TAG) { "Resolution changed to $resolution, restarting capture" }
            stop()
            start()
        }
    }

    private companion object {
        private val highResolutions =
            arrayOf<Dimension>(
                WebcamResolution.FHD.size,
                WebcamResolution.WUXGA.size,
                WebcamResolution.HDP.size,
                WebcamResolution.UXGA.size,
            )

        private val mediumResolutions =
            arrayOf<Dimension>(
                WebcamResolution.HD.size,
                WebcamResolution.WXGA2.size,
                WebcamResolution.SXGA.size,
                WebcamResolution.XGA.size,
            )

        private val lowResolutions =
            arrayOf<Dimension>(
                WebcamResolution.VGA.size,
                WebcamResolution.SVGA.size,
                WebcamResolution.HVGA.size,
                WebcamResolution.QVGA.size,
            )

        private fun resolutionsFor(resolution: CameraResolution): Array<Dimension> =
            when (resolution) {
                CameraResolution.Low -> lowResolutions
                CameraResolution.Medium -> mediumResolutions + lowResolutions
                CameraResolution.High -> highResolutions + mediumResolutions + lowResolutions
            }
    }
}
