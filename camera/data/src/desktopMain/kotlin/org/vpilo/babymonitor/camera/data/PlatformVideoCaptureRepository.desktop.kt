package org.vpilo.babymonitor.camera.data

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.camera.data.ktx.sizes
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.VideoCaptureRepository
import java.awt.Dimension
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

actual class PlatformVideoCaptureRepository(
    webcamGetter: () -> Webcam = { Webcam.getDefault() },
) : VideoCaptureRepository, SharedResourceRepository<CameraFrame>(
    bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
) {

    private var videoCaptureJob: Job? = null

    private val webcam: Webcam = webcamGetter()

    override val frames: CameraFrameFlow = collector.asSharedFlow()

    override fun start() {
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
            coroutineScope.launch {
                while (isActive && webcam.isOpen) {
                    if (!webcam.isImageNew) {
                        delay(10.milliseconds)
                        continue
                    }
                    webcam.getImage()
                        ?.let { image -> collector.tryEmit(CameraFrame(image)) }
                        ?: run {
                            Logger.w(TAG) { "Failed to capture image" }
                            delay(100.milliseconds)
                        }
                }

                delay(1.milliseconds)
            }
                .apply {
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

    override val TAG = PlatformVideoCaptureRepository::class

    private companion object {
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
